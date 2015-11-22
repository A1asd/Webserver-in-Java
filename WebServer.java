import java.io.*;
import java.net.*;
import java.util.*;

public final class WebServer
{
	public static void main(String[] args) throws Exception
	{
		//get new mime.types path if argument -mime was given.
		File mimefile = new File("./mime.types");
		if(args.length==2 && args[0].equals("-mime"))
		{
			String pathToMime = args[1];
			mimefile = new File(pathToMime+"mime.types");
			if (mimefile.exists()) {
				//SUCCSESS!! users mime.types exists. print out those good news
				System.out.println("Your mime.type file will now be searched at " + pathToMime);
			} else {
				//if file does not exist print out instrucions on how to use mime
				System.out.println("Use -mime to specify a path where mime.types exists.");
				System.exit(0);
			} 
		}else if(args.length==1 && args[0].equals("-mime")){
			//exit if only -mime as an argument was given and print out instruction how to use -mime
			System.out.println("Type -mime /your/file/path if you want to specify a path for your mime.types");
			System.exit(0);
		} else if(args.length>0 && !args[0].equals("-mime")){
			//exit if no valid argument (-mime) was given
			System.out.println("No valid argument");
			System.exit(0);
		} else {
			//if no path was specified use default file in directory
			mimefile = new File("./mime.types");
		}

		//make a hashmap to store content types
		HashMap mimehash = new HashMap(999);
		fillHashMap(mimehash, mimefile);

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
		//read out all content types and file extensions and store them to mimehash
		BufferedReader br = new BufferedReader(new FileReader(mimefile));
		String line;
		while((line = br.readLine()) != null) {
			String[] mimeSplit = line.split("\\s");
			if(!mimeSplit[0].contains("#")){ //no need for comments
				for(int i = 0; i<mimeSplit.length; i++){
					mimehash.put(mimeSplit[i], mimeSplit[0]);
				}
			}
		}
	}
}

final class HttpRequest implements Runnable
{
	final static String CRLF = "\r\n";
	Socket socket;
	HashMap mimehash;

	//Constructor
	public HttpRequest(Socket socket, HashMap mimehash) throws Exception
	{
		this.socket = socket;
		this.mimehash = mimehash;
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

		//HTTP1.0 Response 501 Not Implemented, if get, header nor post is in requestLine
		if (!requestLine.contains("GET") || !requestLine.contains("HEAD") || !requestLine.contains("POST")) {
			statusLine = "HTTP/1.0 501 NOT IMPLEMENTED" + CRLF;
			contentTypeLine = "" + CRLF;
		}

		if (fileExists){ //everything ok! print out website/send bytes
			statusLine = "HTTP/1.0 200 OK" + CRLF;
			contentTypeLine = "Content-type: " + contentType(fileName, mimehash) + CRLF;
			System.out.println(contentTypeLine);
		} else { //nothing found here. print out HTTP1.0 Response 404
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
		} else { //HTTP1.0 Response 500 if connection gets lost or something else is nor working right
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

	private static String contentType(String fileName, HashMap mimehash)
	{
		try{
			String extension = "";
			int i = fileName.lastIndexOf(".");
			if (i>0){
				//get extension out of filename
				extension = fileName.substring(i+1);
			}
			//look for extension in mimehash
			if(mimehash.containsKey(extension)){
				//if extension is in mimehash print out its value which should be something like text/html or else...
				return mimehash.get(extension).toString();
			}
		} catch (NullPointerException e){//NullPointerException can be a possible exception here
		}
		//print "application/octet-stream" if extension is unknown
		return "application/octet-stream";
	}
}
