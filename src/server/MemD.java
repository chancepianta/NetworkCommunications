package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
//
// Chance Pianta  cp33252
// Abe Arredondo  aa44757
// EE 382N: Distributed Systems Opt III PRC, Fall 2015
// Dr Vijay K. Garg Professor, Wei-Lun-Hung Teaching Assistant 
// The University of Texas at Austin
// Cockrell School of Engineering 
// September 17th, 2015
//
public class MemD {
	ServerSocket serverSocket;
	
	Clients clients;
	
	public MemD(int tcpPort) throws IOException {
		serverSocket = new ServerSocket(tcpPort);
		clients = clients.INSTANCE;
	}
	
	public void startServer() {
		try {
			while ( true ) {
				Socket clientSocket = serverSocket.accept();
				Thread thread = new Thread(new Client(clientSocket, this.clients));
				thread.start();
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	private String handleInput(String input) {
		String returnValue = "";
		StringTokenizer tokenizer = new StringTokenizer(input);
		String command = tokenizer.nextToken();
		if ( command == null ) {
			return returnValue;
		}
		return returnValue;
	}
	
	public class Client implements Runnable{

	    protected Socket clientSocket;
	    protected Clients clients;

	    public Client(Socket clientSocket, Clients clients) {
	        this.clientSocket = clientSocket;
	        this.clients = clients;
	    }

	    public void run() {
	        try {
	        	BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				DataOutputStream stream = new DataOutputStream(clientSocket.getOutputStream());
				
				String helo = reader.readLine();
				System.out.println(helo);
				if ( helo != null && !helo.isEmpty() && helo.startsWith("HELO") ) {
					String[] heloParts = helo.split(" ");
					if ( clients.hasClient(heloParts[1]) ) {
						System.out.println("RJCT " + heloParts[1] + "\n");
						stream.writeBytes("RJCT " + heloParts[1] + "\n");
					} else {
						// Add connecting client to list of current clients
						clients.addClient(heloParts[1], helo.replaceAll("HELO ", "").replaceAll("\n", "").trim());
						
						// Send accept message back to connecting client
						StringBuilder builder = new StringBuilder();
						builder.append("ACPT ");
						boolean isFirst = true;
						for (String client : clients.getClients()) {
							if ( !isFirst ) {
								builder.append(":");
							} else {
								isFirst = false;
							}
							builder.append(clients.getClient(client));
						}
						builder.append("\n");
						stream.writeBytes(builder.toString());
						
						// Notify other current clients of the new client
						notifyClients("JOIN " + helo.replace("HELO ", "").replace("\n", "").trim() + "\n");
						
						String exit = "";
						while ( ( ( (exit = reader.readLine()) == null ) || !exit.startsWith("EXIT") )
								&& clientSocket.isConnected() ) {}
						
						clients.removeClient(heloParts[1]); // remove exited client from list of current clients
						
						notifyClients("EXIT " + heloParts[1].trim() + "\n"); // notify the other clients of departure
					}
				} else {
					throw new Exception("failed to read message");
				}
	        } catch (Exception e) {
	        	System.out.println(e.getMessage());
	        } finally {
	        	if ( clientSocket != null ) {
	        		try { clientSocket.close(); } catch (Exception e) {}
	        	}
	        }
	    }
	    
	    private void notifyClients(String message) {
	    	for (String client : clients.getClients()) {
	    		// Expect to store the client strings in the format
	    		// <screenname> <hostname> <port>
	    		// So clients parts will be a string array of index 0 = screename, index 1 = hostname, and index 2 = port
	    		String[] clientParts = clients.getClient(client).split(" ");
	    		try {
	    			// Send out the message via UDP
	    			InetAddress address = InetAddress.getByName(clientParts[1]);
	    			DatagramSocket dSocket = new DatagramSocket();
	    			byte[] byteBuffer = message.getBytes();
	    			DatagramPacket dPacket = new DatagramPacket(byteBuffer, byteBuffer.length, address, Integer.parseInt(clientParts[2].trim()));
	    			dSocket.send(dPacket);
	    			dSocket.close();
	    		} catch (Exception e) {
	    			System.out.println(e.getMessage());
	    		}
	    	}
	    }
	}
	
	public enum Clients {
		INSTANCE;
		
		Map<String,String> map = new HashMap<String,String>();
		
		public boolean hasClient(String client) {
			return map.containsKey(client);
		}
		
		public void addClient(String client, String value) {
			map.put(client, value);
		}
		
		public String getClient(String client) {
			if ( map.containsKey(client) ) {
				return map.get(client);
			}
			return "";
		}
		
		public void removeClient(String client) {
			if ( map.containsKey(client) ) {
				map.remove(client);
			}
		}
		
		public Set<String> getClients() {
			return map.keySet();
		}
		
		public int size() {
			return map.size();
		}
	}
	
	public static void main (String[] args) {
	    int tcpPort;
	    if (args.length != 1) {
	      System.out.println("Usage: java YMemD <tcp_port>");
		  System.exit(-1);
		}
	    
	    tcpPort = Integer.parseInt(args[0]);
	    
	    try {
	    	MemD server = new MemD(tcpPort);
	    	server.startServer();
	    } catch (Exception e) {
	    	System.out.println(e.getMessage());
	    }
	}
}
