package client;

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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class Chatter {
	List<String> clients;
	
	String screenName;
	String hostname;
	String tcpPort;
	
	private Selector selector;
	private DatagramChannel udpChannel;
	private Socket socket;
	
	public static void main(String[] args) throws NumberFormatException, IOException {
		if ( args.length != 3 ) {
			System.out.println("Usage: java Chatter <screen Name> <Membership_server_addr> <Membership_server_tcp_port>");
			System.exit(-1);
		}
		
		Chatter chatter = new Chatter(args[0], args[1], args[2]);
		chatter.startChatter();
	}
	
	public Chatter(String screenName, String hostname, String tcpPort) {
		try {
			String response = doTCPRequest("HELO", screenName, hostname, tcpPort, "8999");
			System.out.println(response);
			if ( !isNullOrEmpty(response) && response.startsWith("ACPT") ) {
				response = response.replaceAll("ACPT ", "");
				clients = Arrays.asList(response.split(":"));
				System.out.println(screenName + " has joined the chatroom");
				
				selector = Selector.open();
				
				InetAddress address = InetAddress.getByName(hostname);
				SocketAddress udpChannelPort = new InetSocketAddress(address, 8999);
				
				udpChannel = DatagramChannel.open();
				udpChannel.socket().bind(udpChannelPort);
				udpChannel.configureBlocking(false);
				udpChannel.register(selector, SelectionKey.OP_READ);
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
	
	private String handleInput(String input) {
		System.out.println("handleInput " + input);
		String returnValue = "";
		StringTokenizer tokenizer = new StringTokenizer(input);
		String command = tokenizer.nextToken();
		String screenName = tokenizer.nextToken();
		if ( command == null ) {
			return returnValue;
		} else if ( "MESG".equals( command.toLowerCase() ) ) {
			System.out.println(input.replaceAll("MESG ", ""));
		} else if ( "JOIN".equals( command.toLowerCase() ) ) {
			clients.add(input.replaceAll("JOIN ", ""));
			System.out.println(screenName + " has joined the chatroom");
		} else if ( "EXIT".equals( command.toLowerCase() ) ) {
			String clientToRemove = "";
			for (String client : clients) {
				if ( client.startsWith(screenName) ) {
					clientToRemove = client;
				}
			}
			clients.remove(clientToRemove);
			System.out.println(screenName + " has left the building");
		}
		return returnValue;
	}
	
	private void handleUDPClient() {
		try {
			byte[] request = new byte[1024];
			byte[] response;
			
			SocketAddress address = udpChannel.receive(ByteBuffer.wrap(request));
			String requestString = new String(request);
			response = handleInput(requestString).getBytes();
			udpChannel.send(ByteBuffer.wrap(response), address);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private String doTCPRequest(String command, String screenName, String hostName, String tcpPort, String udpPort) {
		String response = "";
		Socket socket = null;
		try {
			if ( socket == null )
				socket = new Socket(hostName, Integer.parseInt(tcpPort));
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintStream stream = new PrintStream(socket.getOutputStream());
			if ( !isNullOrEmpty(screenName) 
					&& !isNullOrEmpty(hostName) 
					&& !isNullOrEmpty(tcpPort) 
					&& !isNullOrEmpty(udpPort) )
				stream.println(command + " " + screenName + " " + hostName + " " + udpPort);
			else
				stream.println(command);
			stream.flush();
			response = reader.readLine();
		} catch (Exception e) {
			response = e.getMessage();
		}
		return response;
	}
	
	private void doUDPRequest(String command, String screenName, String message) {
		try {
			if ( clients != null && !clients.isEmpty() ) {
				for (String client : clients) {
					String[] clientValues = client.split(" ");
					doUDPRequest(clientValues[1], Integer.parseInt(clientValues[2]), command + " " + screenName + ": " + message + "\n");
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public void doUDPRequest(String host, int port, String message) {
		try {
			InetAddress address = InetAddress.getByName(host);
			DatagramSocket socket = new DatagramSocket();
			byte[] outBuffer = message.getBytes();
			DatagramPacket outPacket = new DatagramPacket(outBuffer, outBuffer.length, address, port);
			socket.send(outPacket);
			byte[] inBuffer = new byte[1024];
			DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private boolean isNullOrEmpty(String string) {
		return ( string == null || string.isEmpty() );
	}
}
