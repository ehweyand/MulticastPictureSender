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
 * Multicast Image Receiver
 * Version: 0.1
 * 
 * @author Jochen Luell
 * 
 */
public class ImageReceiver implements KeyListener {
	/* Flags and sizes */
	public static int HEADER_SIZE = 8;

	public static int SESSION_START = 128;

	public static int SESSION_END = 64;

	/*
	 * The absolute maximum datagram packet size is 65507, The maximum IP packet
	 * size of 65535 minus 20 bytes for the IP header and 8 bytes for the UDP
	 * header.
	 */
	private static int DATAGRAM_MAX_SIZE = 65507;

	/* Default values */
	public static String IP_ADDRESS = "225.4.5.6";

	public static int PORT = 4444;

	JFrame frame;

	boolean fullscreen = false;

	JWindow fullscreenWindow = null;

	/**
	 * Revceive method
	 * 
	 * @param multicastAddress
	 *            IP multicast adress
	 * @param port
	 *            Port
	 */
	private void receiveImages(String multicastAddress, int port) {
		boolean debug = true;

		InetAddress ia = null;
		MulticastSocket ms = null;

		/* Constuct frame */
		JLabel labelImage = new JLabel();
		JLabel windowImage = new JLabel();

		frame = new JFrame("Multicast Image Receiver");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(labelImage);
		frame.setSize(300, 10);
		frame.setVisible(true);
		frame.addKeyListener(this);

		/* Construct full screen window */
		fullscreenWindow = new JWindow();
		fullscreenWindow.getContentPane().add(windowImage);
		fullscreenWindow.addKeyListener(this);

		try {
			/* Get address */
			ia = InetAddress.getByName(multicastAddress);

			/* Setup socket and join group */
			ms = new MulticastSocket(port);
			ms.joinGroup(ia);

			int currentSession = -1;
			int slicesStored = 0;
			int[] slicesCol = null;
			byte[] imageData = null;
			boolean sessionAvailable = false;

			/* Setup byte array to store data received */
			byte[] buffer = new byte[DATAGRAM_MAX_SIZE];

			/* Receiving loop */
			while (true) {
				/* Receive a UDP packet */
				DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
				ms.receive(dp);
				byte[] data = dp.getData();

				/* Read header infomation */
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

				if (debug) {
					System.out.println("------------- PACKET -------------");
					System.out.println("SESSION_START = "
							+ ((data[0] & SESSION_START) == SESSION_START));
					System.out.println("SSESSION_END = "
							+ ((data[0] & SESSION_END) == SESSION_END));
					System.out.println("SESSION NR = " + session);
					System.out.println("SLICES = " + slices);
					System.out.println("MAX PACKET SIZE = " + maxPacketSize);
					System.out.println("SLICE NR = " + slice);
					System.out.println("SIZE = " + size);
					System.out.println("------------- PACKET -------------\n");
				}

				/* If SESSION_START falg is set, setup start values */
				if ((data[0] & SESSION_START) == SESSION_START) {
					if (session != currentSession) {
						currentSession = session;
						slicesStored = 0;
						/* Consturct a appropreately sized byte array */
						imageData = new byte[slices * maxPacketSize];
						slicesCol = new int[slices];
						sessionAvailable = true;
					}
				}

				/* If package belogs to current session */
				if (sessionAvailable && session == currentSession) {
					if (slicesCol != null && slicesCol[slice] == 0) {
						slicesCol[slice] = 1;
						System.arraycopy(data, HEADER_SIZE, imageData, slice
								* maxPacketSize, size);
						slicesStored++;
					}
				}

				/* If image is complete dispay it */
				if (slicesStored == slices) {
					ByteArrayInputStream bis = new ByteArrayInputStream(
							imageData);
					BufferedImage image = ImageIO.read(bis);
					labelImage.setIcon(new ImageIcon(image));
					windowImage.setIcon(new ImageIcon(image));

					frame.pack();
				}

				if (debug) {
					System.out.println("STORED SLICES: " + slicesStored);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (ms != null) {
				try {
					/* Leave group and close socket */
					ms.leaveGroup(ia);
					ms.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		/* Handle command line arguments */
		switch (args.length) {
		case 2:
			IP_ADDRESS = args[1];
		case 1:
			PORT = Integer.parseInt(args[0]);
		}

		ImageReceiver receiver = new ImageReceiver();
		receiver.receiveImages(IP_ADDRESS, PORT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
	 */
	public void keyPressed(KeyEvent keyevent) {
		GraphicsDevice device = GraphicsEnvironment
				.getLocalGraphicsEnvironment().getDefaultScreenDevice();

		/* Toggle full screen mode on key press */
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

}
