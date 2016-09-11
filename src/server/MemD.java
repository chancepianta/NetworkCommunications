package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MemD {
	ServerSocket serverSocket;
	
	public MemD(int tcpPort) throws IOException {
		serverSocket = new ServerSocket(tcpPort);
	}
	
	public void startServer() {
		try {
			while ( true ) {
				Socket clientSocket = serverSocket.accept();
				Thread thread = new Thread(new Client(clientSocket, Clients.INSTANCE));
				thread.start();
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public class Client implements Runnable{

	    protected Socket clientSocket;
	    protected Clients clients;

	    public Client(Socket clientSocket, Clients clients) {
	        this.clientSocket = clientSocket;
	        this.clients = clients;
	    }

	    public void run() {
	    	String helo = "";
	    	boolean needsExitNotification = false;
	        try {
	        	BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				DataOutputStream stream = new DataOutputStream(clientSocket.getOutputStream());
				
				helo = reader.readLine();
				if ( helo != null && !helo.isEmpty() && helo.startsWith("HELO") ) {
					String[] heloParts = helo.split(" ");
					if ( clients.hasClient(heloParts[1]) ) {
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
						needsExitNotification = true;
						
						// Wait for an exit message from a client or an abrupt closing of the TCP connection
						String exit = "";
						while ( (exit = reader.readLine()) != null && !exit.startsWith("EXIT") ) {}
					}
				} else {
					throw new Exception("failed to read message");
				}
	        } catch (Exception e) {
	        	System.out.println("error = " + e.getMessage());
	        } finally {
	        	if ( needsExitNotification ) {
	        		// Let the current clients know of this client's exit
	        		String[] heloParts = helo.split(" ");
        			clients.removeClient(heloParts[1]); // remove exited client from list of current clients
        			notifyClients("EXIT " + heloParts[1].trim() + "\n"); // notify the other clients of departure
	        	}
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
