package multicastpicturesender;

import com.sun.image.codec.jpeg.ImageFormatException;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import static multicastpicturesender.ImageSender.COLOUR_OUTPUT;
import static multicastpicturesender.ImageSender.scale;

/**
 *
 * @author evand
 */
public class Sender {

    // Constantes
    public static int HEADER_SIZE = 8;
    public static int MAX_PACKETS = 255;
    public static int SESSION_START = 128;
    public static int SESSION_END = 64;
    public static int DATAGRAM_MAX_SIZE = 65507 - HEADER_SIZE;
    public static int MAX_SESSION_NUMBER = 255;
    public static String OUTPUT_FORMAT = "jpg";
    public static int COLOUR_OUTPUT = BufferedImage.TYPE_INT_RGB;

    /*
    O tamanho máximo de um datagram packet e 65507, o tamanho máximo de um pacote ip é de 
    65535 menos 20 bytes para o IP header e 8 bytes para o header UDP
     */
    // Parâmetros para a conexão e envio
    public static double SCALING = 0.5;
    public static int SLEEP_MILLIS = 2000;
    public static String IP_ADDRESS = "192.168.0.9";
    public static int PORT = 8000;
    public static boolean SHOW_MOUSEPOINTER = true;

    //Captura a tela toda e retorna uma imagem
    public static BufferedImage getScreenshot() throws AWTException,
            ImageFormatException, IOException {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        Rectangle screenRect = new Rectangle(screenSize);

        Robot robot = new Robot();
        BufferedImage image = robot.createScreenCapture(screenRect);

        return image;
    }

    // Converte uma imagem para array de bytes
    public static byte[] bufferedImageToByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    //Muda o tamanho de uma imagem
    public static BufferedImage scale(BufferedImage source, int w, int h) {
        Image image = source
                .getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING);
        BufferedImage result = new BufferedImage(w, h, COLOUR_OUTPUT);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    //Diminui a imagem
    public static BufferedImage shrink(BufferedImage source, double factor) {
        int w = (int) (source.getWidth() * factor);
        int h = (int) (source.getHeight() * factor);
        return scale(source, w, h);
    }

    //Envia uma imagem via multicast
    private boolean sendImage(byte[] imageData, String multicastAddress, int port) {
        InetAddress ia;

        boolean ret = false;
        int ttl = 2;

        try {
            ia = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return ret;
        }

        MulticastSocket ms = null;

        try {
            ms = new MulticastSocket();
            ms.setTimeToLive(ttl);
            DatagramPacket dp = new DatagramPacket(imageData, imageData.length,
                    ia, port);
            ms.send(dp);
            ret = true;
        } catch (IOException e) {
            e.printStackTrace();
            ret = false;
        } finally {
            if (ms != null) {
                ms.close();
            }
        }

        return ret;
    }

    public static void main(String[] args) {
        Sender sender = new Sender();
        int sessionNumber = 0; //usado para utilizar o session end e session start e montar a imagem corretamente
        boolean multicastImages = false;

        //Criando um frame para disparar o envio e dar um feedback visual
        JFrame frame = new JFrame("Envio de imagem por multicast");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel label = new JLabel();
        frame.getContentPane().add(label);
        frame.setVisible(true);
        label.setText("Multicasteando imagem...");

        frame.pack();

        //Inicia o processamento de envio
        try {
            //Tentando mandar várias imagens continuamente...
            while (true) {
                BufferedImage image;

                /* Pega a imagem */
                image = getScreenshot();

                /* Altera o tamanho da imagem */
                image = shrink(image, SCALING);
                byte[] imageByteArray = bufferedImageToByteArray(image, OUTPUT_FORMAT);
                int packets = (int) Math.ceil(imageByteArray.length / (float) DATAGRAM_MAX_SIZE);

                /* Se uma imagem tem mais que o Máximo de pacotes gera um erro */
                if (packets > MAX_PACKETS) {
                    System.out.println("Image is too large to be transmitted!");
                    continue;
                }

                /* Itera sobre as "fatias" da imagem */
                for (int i = 0; i <= packets; i++) {
                    int flags = 0;
                    flags = i == 0 ? flags | SESSION_START : flags;
                    flags = (i + 1) * DATAGRAM_MAX_SIZE > imageByteArray.length ? flags | SESSION_END : flags;

                    int size = (flags & SESSION_END) != SESSION_END ? DATAGRAM_MAX_SIZE : imageByteArray.length - i * DATAGRAM_MAX_SIZE;

                    /* Set additional header */
                    byte[] data = new byte[HEADER_SIZE + size];
                    data[0] = (byte) flags; //flags de inicio e fim de sessão
                    data[1] = (byte) sessionNumber; //sessão que o pacote pertence
                    data[2] = (byte) packets; //numero de pacotes
                    data[3] = (byte) (DATAGRAM_MAX_SIZE >> 8);
                    data[4] = (byte) DATAGRAM_MAX_SIZE; // tamanho do datagrama
                    data[5] = (byte) i; //qual a fatia que está sendo enviada, para organizar no recebimento
                    data[6] = (byte) (size >> 8);
                    data[7] = (byte) size;

                    /* copia a fatia atual para o array */
                    System.arraycopy(imageByteArray, i * DATAGRAM_MAX_SIZE, data, HEADER_SIZE, size);
                    /* envia o pacote multicast*/
                    sender.sendImage(data, IP_ADDRESS, PORT);

                    /* Sai do loop quando o último pacote é enviado */
                    if ((flags & SESSION_END) == SESSION_END) {
                        break;
                    }
                }
                /* Tempo de fôlego para deixar a reprodução mais pausada e mais lenta */
                Thread.sleep(SLEEP_MILLIS);

                /* Aumenta o número de sessões */
                sessionNumber = sessionNumber < MAX_SESSION_NUMBER ? ++sessionNumber : 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
