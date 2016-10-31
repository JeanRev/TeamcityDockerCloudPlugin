package run.var.teamcity.cloud.docker;

import run.var.teamcity.cloud.docker.client.DockerClient;
import run.var.teamcity.cloud.docker.client.StdioInputStream;
import run.var.teamcity.cloud.docker.client.StdioType;
import run.var.teamcity.cloud.docker.client.StreamHandler;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.net.ssl.HttpsURLConnection;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/**
 * Created by jr on 31.07.16.
 */
public class Test {

    private static class Job implements Runnable {
        volatile int cpt = 0;



        public Integer call() throws Exception {
            System.out.println("Job called");
            return cpt++;
        }

        @Override
        public void run() {
            System.out.println("Called");
            cpt++;
        }
    }
    public static void main(String[] args) throws URISyntaxException, IOException, ExecutionException, InterruptedException {

        /*
        HttpClient client = HttpClientBuilder.create().setConnectionManager(new
                PoolingHttpClientConnectionManager()).build();
        HttpGet get = new HttpGet("http://google.com");
        HttpResponse response = client.execute(get);

        response.getEntity().getContent();
        */


        /*

        ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                System.out.println("AFTER EXEC");
            }
        };
        ScheduledFuture<?> future = service.scheduleAtFixedRate(new Job(), 0, 1, TimeUnit
                .SECONDS);

        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            System.out.println("Done ?: " + future.isDone());
            //System.out.println("Cpt: " + future.get());
        }

        future.cancel(true);
        */


        /*
        byte[] chars = new byte[] { (byte) 0x80, (byte) 0x81 };
        Charset charset = Charset.forName("IBM1098");
        CharsetDecoder decoder = charset.newDecoder();
        String s = new String(chars, "IBM1098");
        System.out.println("Direct: " + s);
        ByteArrayInputStream bais = new ByteArrayInputStream(chars);
        InputStreamReader reader = new InputStreamReader(bais);
        System.out.println("From stream reader: " + reader.read() + " " + reader.read());
        bais = new ByteArrayInputStream(chars);
        reader = new InputStreamReader(bais, charset.newDecoder());
        System.out.println("From stream reader with decoder: " + reader.read() + " " + reader.read());
        */

        //Job job = new Job();


        System.out.println(System.getProperty("java.home"));
        System.out.println(System.getProperties().getProperty("javax.net.ssl.keyStore"));


        //System.getProperties().put("javax.net.ssl.keyStore", "/home/jr/src/teamcity-docker-cld-plugin/client.jks");
        //System.getProperties().put("javax.net.ssl.keyStorePassword", "changeit");
        byte[] buf = new byte[4096];
        int c = new URL("https://127.0.0.1:2376/version").openConnection().getInputStream().read(buf);
        System.out.println("Read from stream: " + new String(buf, 0, c));



        DockerClient client = DockerClient.open(new URI("tcp://127.0.0.1:2376"), true, 2);
        System.out.println("From client: " + client.getVersion());

        client.close();
        if (true) {
            return;
        }


        Node containerSpec = Node.EMPTY_OBJECT.editNode().put("Image", "jetbrains/teamcity-agent:10.0.1").saveNode();

        //client.createContainer(containerSpec);

        //try (final StreamHandler handler = client.attach("some_container")) {

        try (final StreamHandler handler = client.streamLogs("some_container", 10, StdioType.all())) {

            final Thread mt = Thread.currentThread();

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    PrintWriter pw = new PrintWriter(handler.getOutputStream());
                    pw.println("echo hello you 222");
                    pw.close();

                    /*
                    System.out.println("closing stream now");
                    try {
                        handler.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/

                }
            };

            new Thread(runnable).start();

            FileOutputStream fos = new FileOutputStream("/tmp/test.log");

            byte[] buffer = new byte[4096];



            while(true) {
                try (StdioInputStream streamFragment = handler.getNextStreamFragment()) {
                    if (streamFragment == null) {
                        break;
                    }

                    System.out.println("On " + streamFragment.getType());
                    System.out.println(DockerCloudUtils.readUTF8String(streamFragment));
                }
            }


            /*
            try (StdioInputStream is = handler.getNextStreamFragment()) {
                assert is != null;
                int n;
                while((n = is.read(buffer)) != -1) {
                    System.out.print(new String(buffer, 0, n));
                    fos.write(buffer, 0, n);
                    fos.flush();
                }
            }
            fos.close();
            */

            /*
            InputStream stream = null;



            PrintWriter pw = new PrintWriter(handler.getOutputStream());
            while ((stream = handler.getStreamFragment()) != null) {
                System.out.println("Will output on: " + handler.getType());
                System.out.println(DockerCloudUtils.readUTF8String(stream));
                pw.write("echo hello world\n");
                pw.flush();
            }
            pw.close();
            */
        }
        /*
        try (InputStream inputStream = client.getLogs("some_container", 5)){
            String logs = DockerCloudUtils.readUTF8String(inputStream);
            System.out.println("Logs found:\n"  + logs.replaceAll("\u001B\\[[\\d;]*[^\\d;]",""));
        } finally {
            client.close();
        }*/

    }
}
