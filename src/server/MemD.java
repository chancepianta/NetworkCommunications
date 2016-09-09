package server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
				if ( helo != null && !helo.isEmpty() && helo.startsWith("HELO") ) {
					String[] heloParts = helo.split(" ");
					if ( clients.hasClient(heloParts[1]) ) {
						stream.writeBytes("RJCT " + heloParts[1] + "\n");
					}
					
					clients.addClient(heloParts[1], helo.replaceAll("HELO ", "").replaceAll("\n", "").trim());
					
					StringBuilder builder = new StringBuilder();
					builder.append("ACPT ");
					for (String client : clients.getClients()) {
						builder.append(clients.getClient(client));
						if ( clients.size() > 1 ) {
							builder.append(":");
						}
					}
					builder.append("\n");

					stream.writeBytes(builder.toString());
					
					String exit = null;
					while ( (exit = reader.readLine()) == null ) {}
					
					System.out.println(exit);
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
