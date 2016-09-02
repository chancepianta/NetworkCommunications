package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Chatter {
	public static void main(String[] args) {
		String screenName;
		String hostName;
		String tcpPort;
		
		if ( args.length != 3 ) {
			System.out.println("Usage: java Chatter <screen Name> <Membership_server_addr> <Membership_server_tcp_port>");
			System.exit(-1);
		}
		
		screenName = args[0];
		hostName = args[1];
		tcpPort = args[2];
		
		try {
			String response = doTCPRequest("HELO", screenName, hostName, tcpPort);
			if ( !isNullOrEmpty(response) && response.startsWith("ACPT") ) {
				response = response.replaceAll("ACPT ", "");
				List<String> clients = Arrays.asList(response.split(":"));
				//Scanner sc = new Scanner(System.in);
				
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		} finally {
			doTCPRequest("EXIT", null, null, null);
		}
	}
	
	public static String doTCPRequest(String command, String screenName, String hostName, String tcpPort) {
		String response = "";
		Socket socket = null;
		try {
			socket = new Socket(hostName, Integer.parseInt(tcpPort));
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintStream stream = new PrintStream(socket.getOutputStream());
			if ( !isNullOrEmpty(screenName) && !isNullOrEmpty(hostName) && !isNullOrEmpty(tcpPort) )
				stream.println(command + " " + screenName + " " + hostName + " " + tcpPort);
			else
				stream.println(command);
			stream.flush();
			response = reader.readLine();
		} catch (Exception e) {
			response = e.getMessage();
		} finally {
			if ( socket != null && !socket.isClosed() ) {
				try { socket.close(); } catch (Exception e) {}
			}
		}
		return response;
	}
	
	private static boolean isNullOrEmpty(String string) {
		return ( string == null || string.isEmpty() );
	}
}
