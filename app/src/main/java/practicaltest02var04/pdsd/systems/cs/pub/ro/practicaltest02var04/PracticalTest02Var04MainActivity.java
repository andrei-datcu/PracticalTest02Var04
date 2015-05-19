package practicaltest02var04.pdsd.systems.cs.pub.ro.practicaltest02var04;

import android.app.Activity;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;


public class PracticalTest02Var04MainActivity extends Activity {

    protected boolean serverRunning = false;
    protected ServerThread server;
    protected Button serverButton, queryButton;
    protected TextView serverStatusText;
    protected UrlInfo urlInfo = new UrlInfo();
    protected Handler handler;

    private class UrlInfo {
        private HashMap<String, String> data = new HashMap<String, String>();

        public synchronized void putData(String url, String info) {
            data.put(url, info);
        }

        public synchronized String getData(String url) {
            return data.get(url);
        }
    }

    private class CommThread extends Thread {
        private Socket socket;
        UrlInfo info;

        public CommThread(UrlInfo info, Socket socket) {
            this.socket = socket;
            this.info = info;
        }

        public void run(){
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

                String url = br.readLine();

                if (url == null || url.isEmpty()) {
                    pw.println("Malformed request");
                    socket.close();
                    return;
                }

                String content = info.getData(url);
                if (content == null) {
                    // We have new Url

                    Log.d("Server", "Fac cerere http. Nu s-a gasit in cache");
                    HttpClient httpClient = new DefaultHttpClient();
                    URL urlobj = new URL(url);
                    HttpGet httpGet = new HttpGet(urlobj.toString());
                    if (httpGet == null) {
                        Log.e("Server", "Malformed response");
                        pw.println("Cannot retrieve response. Probably the url is wrong.");
                        socket.close();
                        return;
                    }
                    HttpResponse response = httpClient.execute(httpGet);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != HttpURLConnection.HTTP_OK) {
                        Log.e("Server", "Cod de eroare diferit de 200: " + statusCode);
                        pw.println("Try later. Error code: " + statusCode);
                        socket.close();
                        return;
                    }

                    content = EntityUtils.toString(response.getEntity());
                    info.putData(url, content);
                }

                pw.println(content);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class ServerThread extends Thread {
        private boolean isRunning = false;
        private ServerSocket serverSocket;
        private int port;
        private UrlInfo info;

        public ServerThread(int port, UrlInfo info) {
            this.port = port;
            this.info = info;
        }

        public void startServer() {
            isRunning = true;
            start();
        }

        public void stopServer() {
            isRunning = false;
            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (serverSocket != null)
                        try {
                            serverSocket.close();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    serverButton.setText("Start sever");
                                    serverStatusText.setText("Serverul este oprit");
                                    serverButton.setEnabled(true);
                                }
                            });
                        } catch (IOException e) {
                        }
                }
            }).start();

        }

        public void run() {
            try {
                serverSocket = new ServerSocket(port);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        serverButton.setText("Stop server");
                        serverStatusText.setText("Serverul este pornit");
                        serverButton.setEnabled(true);
                        queryButton.setEnabled(true);
                    }
                });

                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    new CommThread(info, socket).start();
                }
            } catch (IOException e) {
                Log.e("IOException", e.getMessage());

            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practical_test02_var04_main);

        handler = new Handler();

        serverButton = (Button)findViewById(R.id.serverButton);
        queryButton = (Button)findViewById(R.id.queryButton);
        serverStatusText= (TextView)findViewById(R.id.serverStatus);
        final WebView queryResult = (WebView)findViewById(R.id.queryWebView);
        final EditText portText = (EditText)findViewById(R.id.serverPortText);
        final EditText urlText = (EditText)findViewById(R.id.urlText);

        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryButton.setEnabled(false);
                if (serverRunning) {
                    server.stopServer();
                    serverButton.setEnabled(false);
                    serverStatusText.setText("Serverul se opreste...");
                } else {
                    String portstr = portText.getText().toString();

                    if (portstr == null || portstr.isEmpty()) {
                        Toast.makeText(getApplicationContext(), "Completati portul!!!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int port = Integer.parseInt(portText.getText().toString());
                    if (port < 1024 || port >= 65536) {
                        Toast.makeText(getApplicationContext(), "Portul trebuie sa fie un numar intre 1024 - 65 536!!!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    server = new ServerThread(port, urlInfo);
                    serverButton.setEnabled(false);
                    serverStatusText.setText("Serverul porneste...");
                    server.startServer();
                }
                serverRunning = !serverRunning;
            }
        });


        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String url = urlText.getText().toString();

                if (url.isEmpty() || url.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Completati URL-ul!!!", Toast.LENGTH_LONG).show();
                    return;
                }

                final int port = Integer.parseInt(portText.getText().toString());

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Socket socket = new Socket("localhost", port);
                            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            PrintWriter socketPw = new PrintWriter(socket.getOutputStream(), true);

                            // Building Request
                            socketPw.println(url);

                            String line;
                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            while ((line = br.readLine()) != null) {
                                pw.println(line);
                            }

                            final String queryText = sw.toString();
                            queryResult.post(new Runnable() {
                                @Override
                                public void run() {
                                    queryResult.loadDataWithBaseURL(url,queryText, "text/html", "UTF-8", null);
                                }
                            });
                            socket.close();
                        } catch (IOException e) {
                            Log.e("IOException", e.getMessage());
                        }

                    }
                }).start();
            }
        });
    }
}
