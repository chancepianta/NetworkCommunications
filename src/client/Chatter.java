package client;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class Chatter extends JFrame {
	// List of clients, string values in the list
	// should follow format off '<screen name> <hostname> <udp port>'
	// This list should be populated originally with the data from the server's
	// ACTP message and then clients will be added/removed in accordance to
	// JOIN and EXIT messages sent from the membership server
	private List<String> clients;
	
	// Screen name of user logged into client
	private String screenName;
	
	// Attributes for UDP server
	private Selector selector;
	private DatagramChannel udpChannel;
	
	// Attributes for TCP port to chat server
	private Socket socket = null;
	
	
	// Attributes for GUI
	private JTextField textField;
	private JTextArea textArea;
	private JButton button;
	
	public static void main(String[] args) throws NumberFormatException, IOException {
		if ( args.length != 3 ) {
			System.out.println("Usage: java Chatter <screen Name> <Membership_server_addr> <Membership_server_tcp_port>");
			System.exit(-1);
		}
		
		Chatter chatter = new Chatter(args[0], args[1], args[2]);
		chatter.startChatter();
	}
	
	public Chatter(String screenName, String hostname, String tcpPort) {
		super(screenName); // Call JFrame's constructor to set the title of the window
		try {
			this.screenName = screenName;
			
			selector = Selector.open();
			
			InetAddress address = InetAddress.getByName("localhost");
			// Get a port for our UDP server, passing a value of 0 will mean that the OS
			// will assign us an available port to use
			SocketAddress udpChannelPort = new InetSocketAddress(address, 0);
			
			// Start the UDP server in a non blocking mode
			udpChannel = DatagramChannel.open();
			udpChannel.socket().bind(udpChannelPort);
			udpChannel.configureBlocking(false);
			udpChannel.register(selector, SelectionKey.OP_READ);
			
			System.out.println("My port is: " + udpChannel.getLocalAddress().toString().split(":")[1]); // Our UDP server port
			// Let the chat server know that we wish to connect and how to reach us
			String response = doTCPRequest("HELO", screenName, hostname, tcpPort, udpChannel.getLocalAddress().toString().split(":")[1]);
			if ( !isNullOrEmpty(response) && response.startsWith("ACPT") ) {
				// Remove the ACTP portion of our response message
				// This should then leave us with just the ':' separated list of clients
				response = response.replaceAll("ACPT ", "");
				// Split the clients up into a List data structure for easier handling of clients
				clients = new ArrayList<String>();
				clients.addAll(Arrays.asList(response.split(":")));
				
				Container container = getContentPane();
				
				// Create our text input field
				textField = new JTextField("Enter here:");
				textField.setEnabled(true);
				// Add and action listener for when the user enters text
				textField.addActionListener(
							new ActionListener() {
								public void actionPerformed(ActionEvent actionEvent) {
									try {
										// Send the input text to the other clients
										doUDPRequest("MESG", screenName, actionEvent.getActionCommand());
										// Added the entered text to the text area of the gui
										textArea.append(screenName + ": " + actionEvent.getActionCommand() + "\n");
										// Clear out the text input field
										textField.setText("");
									} catch (Exception e) {
										System.out.println("failed to send message " + actionEvent.getActionCommand());
										System.out.println(e.getMessage());
									}
								}
							});
				container.add(textField, BorderLayout.NORTH);
				
				// Set up our text area
				textArea = new JTextArea();
				container.add(new JScrollPane(textArea), BorderLayout.CENTER);
				
				// Set up our exit button
				button = new JButton("Exit");
				button.setEnabled(true);
				button.addActionListener(
							new ActionListener() {
								public void actionPerformed(ActionEvent actionEvent) {
									doExit();
								}
							});
				container.add(button, BorderLayout.SOUTH);
				
				setSize(640, 480);
				setVisible(true);
				
				addWindowListener(
							new WindowAdapter() {
								public void windowClosing(WindowEvent windowEvent) {
									doExit();
								}
							});
				
				textArea.append("\n" + screenName + " has joined the chatroom\n");
			} else if ( !isNullOrEmpty(response) && response.startsWith("RJCT") ) {
				System.out.println("Screen Name already exists: " + screenName);
				System.exit(-1);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public void startChatter() {
		try {
			while ( true ) {
				selector.select();
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = keys.iterator();
				while ( iterator.hasNext() ) {
					SelectionKey key = iterator.next();
					iterator.remove();
					Channel channel = key.channel();
					if ( key.isReadable() && channel == udpChannel ) {
						handleUDPClient();
					}
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private void doExit() {
		doTCPRequest("EXIT\n", null, null, null, null);
		if ( socket != null ) {
			try {
				socket.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
		System.exit(0);
	}
	
	private String handleInput(String input) {
		String returnValue = "";
		StringTokenizer tokenizer = new StringTokenizer(input);
		String command = tokenizer.nextToken();
		String screenName = tokenizer.nextToken();
		if ( command == null || this.screenName.equalsIgnoreCase(screenName.replaceAll(":", "")) ) {
			return returnValue;
		} else if ( "MESG".equalsIgnoreCase(command) ) {
			textArea.append(input.replaceAll("MESG ", ""));
		} else if ( "JOIN".equalsIgnoreCase(command) ) {
			clients.add(input.replaceAll("JOIN ", ""));
			textArea.append(screenName + " has joined the chatroom\n");
		} else if ( "EXIT".equalsIgnoreCase(command) ) {
			String clientToRemove = "";
			for (String client : clients) {
				if ( client.startsWith(screenName) ) {
					clientToRemove = client;
				}
			}
			clients.remove(clientToRemove);
			textArea.append(screenName + " has left the building\n");
		}
		return returnValue;
	}
	
	private void handleUDPClient() {
		try {
			byte[] request = new byte[1024];
			udpChannel.receive(ByteBuffer.wrap(request));
			String requestString = new String(request);
			handleInput(requestString).getBytes();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private String doTCPRequest(String command, String screenName, String hostName, String tcpPort, String udpPort) {
		String response = "";
		try {
			if ( socket == null )
				socket = new Socket(hostName, Integer.parseInt(tcpPort));
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintStream stream = new PrintStream(socket.getOutputStream());
			if ( !isNullOrEmpty(screenName) 
					&& !isNullOrEmpty(hostName) 
					&& !isNullOrEmpty(udpPort) ) {
				stream.println(command + " " + screenName + " " + hostName + " " + udpPort);
				stream.flush();
				response = reader.readLine();
			} else {
				stream.println(command);
				stream.flush();
			}
		} catch (Exception e) {
			response = e.getMessage();
		}
		return response;
	}
	
	private void doUDPRequest(String command, String screenName, String message) throws NumberFormatException, IOException {
		if ( clients != null && !clients.isEmpty() ) {
			for (String client : clients) {
				String[] clientValues = client.split(" ");
				if ( !clientValues[0].equalsIgnoreCase(screenName) ) {
					String port = clientValues[2];
					doUDPRequest(clientValues[1], new Integer(port.replaceAll("\n", "").trim()), command + " " + screenName + ": " + message + "\n");
				}
			}
		}
	}
	
	private void doUDPRequest(String host, int port, String message) throws IOException {
		InetAddress address = InetAddress.getByName(host);
		DatagramSocket dSocket = new DatagramSocket();
		byte[] outBuffer = message.getBytes();
		DatagramPacket outPacket = new DatagramPacket(outBuffer, outBuffer.length, address, port);
		dSocket.send(outPacket);
		dSocket.close();
	}
	
	private boolean isNullOrEmpty(String string) {
		return ( string == null || string.isEmpty() );
	}
}
