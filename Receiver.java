package multicastpicturesender;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JWindow;

/**
 *
 * @author evand
 */
public class Receiver implements KeyListener {

    // Constantes
    public static int HEADER_SIZE = 8;
    public static int SESSION_START = 128;
    public static int SESSION_END = 64;

    /*
    O tamanho máximo de um datagram packet e 65507, o tamanho máximo de um pacote ip é de 
    65535 menos 20 bytes para o IP header e 8 bytes para o header UDP
     */
    private static int DATAGRAM_MAX_SIZE = 65507;

    /* Configurações e valores para trabalhar com o envio */
    public static String IP_ADDRESS = "192.168.0.9";
    public static int PORT = 8000;
    //Configurações de exibição
    JFrame frame;
    boolean fullscreen = false;
    JWindow fullscreenWindow = null;

    // Coordena o evento de teclado
    public void keyPressed(KeyEvent keyevent) {
        GraphicsDevice device = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice();

        /* Muda para fullscreen quando pressionado */
        if (fullscreen) {
            device.setFullScreenWindow(null);
            fullscreenWindow.setVisible(false);
            fullscreen = false;
        } else {
            device.setFullScreenWindow(fullscreenWindow);
            fullscreenWindow.setVisible(true);
            fullscreen = true;
        }
        
    }
    
    public void keyReleased(KeyEvent keyevent) {
    }
    
    public void keyTyped(KeyEvent keyevent) {
    }

    // Método principal para receber imagens
    private void receiveImages(String multicastAddress, int port) {
        
        boolean debug = true;
        InetAddress ia = null;
        MulticastSocket ms = null;

        /* Construir o frame para exibição */
        JLabel labelImg = new JLabel();
        JLabel windowImg = new JLabel();
        
        frame = new JFrame("Recebedor de imagens multicast");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(labelImg);
        frame.setSize(300, 10);
        frame.setVisible(true);
        //adiciona o evento ao frame (evento de tecla)
        frame.addKeyListener(this);

        /* Constroi a janela em modo tela cheia */
        fullscreenWindow = new JWindow();
        fullscreenWindow.getContentPane().add(windowImg);
        fullscreenWindow.addKeyListener(this);
        
        try {
            /* Pega IP */
            ia = InetAddress.getByName(multicastAddress);

            /* Configura o socket e entra no grupo (join) */
            ms = new MulticastSocket(port);
            ms.joinGroup(ia);
            
            int currentSession = -1; //sessão inicia negativa para controle
            int slicesStored = 0; //fatias armazenadas
            int[] slicesCol = null;
            byte[] imageData = null;
            boolean sessionAvailable = false;

            /* Array de bytes para armazenar o que é recebido */
            byte[] buffer = new byte[DATAGRAM_MAX_SIZE];

            /* Loop para receber os dados */
            while (true) {
                /* Recebe um pacote UDP */
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                ms.receive(dp);
                byte[] data = dp.getData();

                /* Faz a leitura do header para organizar */
                short session = (short) (data[1] & 0xff);
                short slices = (short) (data[2] & 0xff);
                int maxPacketSize = (int) ((data[3] & 0xff) << 8 | (data[4] & 0xff)); // mask
                // the
                // sign
                // bit
                short slice = (short) (data[5] & 0xff);
                int size = (int) ((data[6] & 0xff) << 8 | (data[7] & 0xff)); // mask
                // the
                // sign
                // bit

                /* SESSION_START True, configura os valores iniciais */
                if ((data[0] & SESSION_START) == SESSION_START) {
                    if (session != currentSession) {
                        currentSession = session;
                        slicesStored = 0;
                        /* Constroi um array de byte de tamanho apropriado */
                        imageData = new byte[slices * maxPacketSize];
                        slicesCol = new int[slices];
                        sessionAvailable = true;
                    }
                }

                /* Se o pacote pertence a sessão atual, captura o pedaço da imagem
                e faz o controle enviando ao array*/
                if (sessionAvailable && session == currentSession) {
                    if (slicesCol != null && slicesCol[slice] == 0) {
                        slicesCol[slice] = 1;
                        System.arraycopy(data, HEADER_SIZE, imageData, slice
                                * maxPacketSize, size);
                        slicesStored++;
                    }
                }

                /* Se a imagem estiver completa, exibe ela*/
                if (slicesStored == slices) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(
                            imageData);
                    BufferedImage image = ImageIO.read(bis);
                    labelImg.setIcon(new ImageIcon(image));
                    windowImg.setIcon(new ImageIcon(image));
                    
                    frame.pack();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ms != null) {
                try {
                    /* Sai do grupo e fecha o socket */
                    ms.leaveGroup(ia);
                    ms.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    public static void main(String[] args) {
        Receiver receiver = new Receiver();
        receiver.receiveImages(IP_ADDRESS, PORT);
    }
}
