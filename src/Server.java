import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class Server{
	private ServerSocket serverSocket; 
	
	private int listeningPort;
	
	public volatile boolean isRunning;	
		
	private Thread mainThread;
	
	private volatile Map<String, Conn> playRequests;
		
	public Server(int port){
		
		try {
			listeningPort = port;
			serverSocket = new ServerSocket(listeningPort);
			isRunning = true;
			playRequests = new ConcurrentHashMap<String, Conn>();
			mainThread = new Thread(new Runnable(){

				@Override
				public void run() {
					
					handleIncomingConnections();					
				}				
			});
			
		} catch (IOException e) {
			System.out.println(e.toString());
		}
	}

	public void run(){
		mainThread.start();
	}
	
	private void handleIncomingConnections(){
		while(isRunning){				
			try {
				final Socket client = serverSocket.accept();
				System.out.println("Connected");			
				new Thread(new Runnable(){

					@Override
					public void run() {
						try{
							BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
							PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true);  
			                //get id, get state, get id to play
							String read = null;
							String id = null;
							boolean isRequesting = false;
							String rid = null;
							while(!(read = reader.readLine()).equals("FIN_1")){
								String[] str = read.split("#");
								if(str[0].equals("id")){
									id = str[1];
								}else if(str[0].equals("isRequesting")){
									isRequesting = (str[1].equals("1"));
								}else if(str[0].equals("rid")){
									rid = str[1];
								}
							}
							Conn connection = new Conn(client, isRequesting, id, writer, reader);
							
							if(isRequesting){
								playRequests.put(rid, connection);
							}else{
								if(playRequests.containsKey(id)){
									Conn conn = playRequests.get(id);
									playRequests.remove(id);
									handleGame(conn, connection);
								}
							}
							
						}catch(Exception e){
							System.out.println(e.toString());
						}
					}
					
				}).start();
				

			} catch (IOException e) {
				System.out.println(e.toString());
			}			
		}
	}
	
	
	private void handleGame(Conn a, Conn b){
		System.out.println(a.id + " " + b.id);
		Thread gameThread = new Thread(new handleGameRunnable(a, b));
		gameThread.start();
	}
	
	private class handleGameRunnable implements Runnable{

		private volatile Conn a;
		private volatile Conn b;	
		private Thread areadThread;
		private Thread breadThread;
		private Thread messageThread;
		private volatile PrintWriter awriter;
		private volatile PrintWriter bwriter;
		private volatile BufferedReader areader;
		private volatile BufferedReader breader;
		private volatile boolean aisReady;
		private volatile boolean bisReady;
		public handleGameRunnable(final Conn s1, final Conn s2){
			this.a = s1;			
			this.b = s2;
			try {
				awriter = a.writer;
				bwriter = b.writer;
				areader = a.reader;
				breader = b.reader;
			} catch (Exception e) {
				System.out.println(e.toString());
			}
			
			messageThread = new Thread(new Runnable(){
				
				@Override
				public void run() {
					while(!(aisReady && bisReady)){
						//wait till both ready
						//TODO not wait for too long
					}
					for(int i = 0; i < 5; i++){
						
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
	                    awriter.println("Hey I'm server " + i); 
	                    bwriter.println("Hey I'm server " + i);  
	                    
					}
				}
				
			});
			
			areadThread = new Thread(new Runnable(){

				@Override
				public void run() {		        	
		        	String line = null;
		        	try {
						while ((line = areader.readLine()) != null){
							if(line.equals("READY")){
								aisReady = true;								
							}else{
								bwriter.println(line);
							}
						}
						a.close();
					} catch (Exception e) {
						System.out.println(e.toString());
					}
				}
				
			});
			
			breadThread = new Thread(new Runnable(){

				@Override
				public void run() {
					String line = null;
		        	try {
						while ((line = breader.readLine()) != null){
							if(line.equals("READY")){
								bisReady = true;
							}else{
								awriter.println(line);
							}
						}
						b.close();
					} catch (Exception e) {
						System.out.println(e.toString());
					}
				}
				
			});
		}			
			
		@Override
		public void run() {
			messageThread.start();
			areadThread.start();
			breadThread.start();			
		}
		
	} 
	
	private class Conn{
		public Socket s;
		public boolean isRequesting;
		public PrintWriter writer;
		public String id;
		public BufferedReader reader;
		Conn(Socket s, boolean isRequesting, String id, PrintWriter writer, BufferedReader reader){
			this.s = s;
			this.isRequesting = isRequesting;
			this.id = id;	
			this.writer = writer;
			this.reader = reader;
		}
		
		public void close() throws IOException{
			s.close();
		}
	}
}
