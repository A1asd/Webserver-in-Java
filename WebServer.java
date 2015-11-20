import java.io.*;
import java.net.*;
import java.util.*;

public final class WebServer
{
	public static void main(String[] args) throws Exception
	{
		//get new mime.types path if argument -mime was given.
		File mimefile;
		if(args.length>0 && args[0].equals("-mime"))
		{
			String pathToMime = args[1];
			mimefile = new File(pathToMime+"mime.types");
			if (mimefile.exists()) {
				System.out.println("Your mime.type file will now be searched at " + pathToMime);
			} else {
				System.out.println("Use -mime to specify a path where mime.types exists.");
				System.exit(0);
			}
		} else {
			mimefile = new File("./mime.types");
		}

		HashMap mimehash = new HashMap(999);
		if (args.length>0) {
			fillHashMap(mimehash, mimefile);
		} else {
			fillHashMap(mimehash, mimefile);
		}

		//Set the port number.
		int port = 6789;
		//Establish the listen socket.
		ServerSocket serverSocket = new ServerSocket(port);
		//Process HTTP service requests in an infinite loop.
		while(true){
			//Listen for a TCP connection request.
			Socket client = serverSocket.accept();
			//Construct an object to process the HTTP request message.
			HttpRequest request = new HttpRequest(client, mimehash);
			//Create a new thread to process the request.
			Thread thread = new Thread(request);
			//Start the thread.
			thread.start();
		}
	}

	public static void fillHashMap(HashMap mimehash, File mimefile) throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(mimefile));
		String line;
		while((line = br.readLine()) != null) {
			String[] mimeSplit = line.split("\\s");
			if(!mimeSplit[0].contains("#")){
				for(int i = 0; i<mimeSplit.length; i++){
					mimehash.put(mimeSplit[0], mimeSplit[i]);
					//System.out.println(mimeSplit[0] + " " + mimeSplit[i]);
				}
			}
		}
	}
}

final class HttpRequest implements Runnable
{
	final static String CRLF = "\r\n";
	Socket socket;

	//Constructor
	public HttpRequest(Socket socket, HashMap mimehash) throws Exception
	{
		this.socket = socket;
	}

	//Implement the run() method of the Runnable Interface.
	public void run()
	{
		try{
			processHttpRequest();
		} catch (Exception e){
			System.out.println(e);
		}
	}

	private void processHttpRequest() throws Exception
	{
		//Get a reference to the socket's input and output streams.
		InputStream is = socket.getInputStream();
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());

		//Set up input stream filters.
		InputStreamReader isp = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isp);

		//Get the request of the HTTP request message.
		String requestLine = br.readLine();

		String getClientIP = socket.getInetAddress().toString();

		//Display the request line.
		System.out.println();
		System.out.println(requestLine);

		//Get and display the header lines.
		String headerLine = CRLF;
		String userAgent = null;
		while((headerLine = br.readLine()).length() != 0){
			if (headerLine.contains("User-Agent")) {
				userAgent = headerLine;
			}
			System.out.println(headerLine);
		}

		//Extract the filename from the request line.
		StringTokenizer tokens = new StringTokenizer(requestLine);
		tokens.nextToken(); //skip over the method, which should be "GET"
		String fileName = tokens.nextToken();

		//Prepend a "." so that file request ist wihtin the current directory.
		fileName = "." + fileName;
		if (fileName.equals("./")) {
			fileName = "./testfiles/index.html";
		}

		//Open the requested file.
		FileInputStream fis = null;
		boolean fileExists = true;
		try{
			fis = new FileInputStream(fileName);
		} catch (FileNotFoundException e){
			fileExists = false;
		}

		//Construct the response message.
		String statusLine = null;
		String contentTypeLine = null;
		String entityBody = null;

		if (!requestLine.contains("GET") || !requestLine.contains("HEAD") || !requestLine.contains("POST")) {
			statusLine = "HTTP/1.0 501 NOT IMPLEMENTED" + CRLF;
			contentTypeLine = "" + CRLF;
		}

		if (fileExists){
			statusLine = "HTTP/1.0 200 OK" + CRLF;
			contentTypeLine = "Content-type: " + contentType( fileName ) + CRLF;
			System.out.println(contentTypeLine);
		} else {
			statusLine = "HTTP/1.0 404 NOT FOUND" + CRLF;
			contentTypeLine = "" + CRLF;
			entityBody = "<HTML>" + "<HEAD><TITLE>404 Not Found</TITLE></HEAD>" + "<BODY><img src=\"/testfiles/404.jpg\"></img></BODY></HTML><br>" + userAgent + "<br>" + getClientIP;
		}

		//Send the status line.
		os.writeBytes(statusLine);

		//Send the content type line.
		os.writeBytes(contentTypeLine);

		//Send a blank line to indicate the end of the header lines.
		os.writeBytes(CRLF);

		//Send the entity body.
		if (fileExists){
			sendBytes(fis, os);
			fis.close();
		} else {
			statusLine = "HTTP/1.0 500 INTERNAL SERVER ERROR" + CRLF;
			contentTypeLine = "" + CRLF;
			os.writeBytes(entityBody);
		}

		//Close streams and socket.
		os.close();
		br.close();
		socket.close();
	}

	private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception
	{
		//Construct a 1K buffer to hold bytes on their way to the socket.
		byte[] buffer = new byte[1024];
		int bytes = 0;

		//Copy requested file into the socket's output stream.
		while((bytes = fis.read(buffer)) != -1){
			os.write(buffer, 0, bytes);
		}
	}

	private static String contentType(String fileName)
	{
		if(fileName.endsWith(".htm") || fileName.endsWith(".html")){
			return "text/html";
		}
		if(fileName.endsWith(".md")) {
			return "text/md";
		}
		//return  contentType(fileName)
		return "application/octet-stream";
	}
}
