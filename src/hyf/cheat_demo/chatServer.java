package hyf.cheat_demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class chatServer {
    private int port;
    private Selector selector;
    private ByteBuffer readbuf=ByteBuffer.allocate(1024);
    private ByteBuffer sendbuf=ByteBuffer.allocate(1024);
    private static final String USER_NAME_TAG = "$%%^&*()!@#$^%#@*()*";
    private HashSet<String> users = new HashSet<String>();//保存用户名
    private HashMap<String ,String > user=new HashMap<>();
    private String user_msg;

    public chatServer(int port){
        this.port=port;
    }

    public static void main(String[] args)  {
        new chatServer(8899).start();
    }

    public void start()  {
        try {
            //获取服务端通道
            ServerSocketChannel sschannel =ServerSocketChannel.open();
            //设置为非阻塞
            sschannel.configureBlocking(false);
            sschannel.bind(new InetSocketAddress(port));
            //创建选择器
            selector=Selector.open();
            //将服务器通道注册到选择器
            sschannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("ChatServer started ......");


        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true){
            try {
                int events =selector.select();
                if(events>0){
                    Iterator<SelectionKey> it =selector.selectedKeys().iterator();
                    while (it.hasNext()){
                        SelectionKey sk =it.next();
                        if(sk.isValid()){
                            if(sk.isAcceptable()){
                                accept(sk);
                            }else if(sk.isReadable()){
                                read(sk);
                            }
                        }
                        it.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    private void accept(SelectionKey key) throws IOException {
        //获取当前就绪事件的通道
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
       //获取请求的客户端通道
        SocketChannel clientChannel =ssc.accept();
        //设置为非阻塞
        clientChannel.configureBlocking(false);
        //绑定到选择器上
        clientChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("a new client connected "+clientChannel.getLocalAddress());
    }
    private void read(SelectionKey sk) throws IOException {
        SocketChannel clientChannel = (SocketChannel) sk.channel();
        this.readbuf.clear();//清空缓冲区准备读数据
        System.out.println("===============read");
        int numRead;
        try {
            numRead= clientChannel.read(this.readbuf);
        }catch (IOException e){ // 客户端断开连接，这里会报错提示远程主机强迫关闭了一个现有的连接。
            offlineUser(sk);
            sk.cancel();//注销断开连接的客户端
            clientChannel.close();
            return;
        }
        readbuf.flip();
        user_msg=new String(readbuf.array(),0,numRead);
        for(String  s:users){
            System.out.println("在线用户"+s);
        }
        if (user_msg.contains(USER_NAME_TAG)){  // 用户第一次登陆， 输入登录名
            String user_name = user_msg.replace(USER_NAME_TAG, "");
            user_msg="欢迎: " + user_name + " 登录聊天室";
            users.add(clientChannel.getRemoteAddress().toString()+"=="+user_name);
            brodcast(clientChannel, user_msg);
        }      else if (user_msg.equals("1")){       // 显示在线人数
            user_msg = onlineUser();
            write(clientChannel, user_msg);
        }else{
            String user="";
            for(String s:users){
                if(s.contains(clientChannel.getRemoteAddress().toString())){
                    String[] s1 = s.split("===");
                    if (s1.length == 2){
                        user = "用户" + s1[1] + "对大家说:";
                    }else{
                        continue;
                    }

                }
            }
            brodcast(clientChannel,user+user_msg);
        }
    }

    /**
     *   在线用户
     */
    private String onlineUser(){
        String online_users = "在线用户:\n";
        String user = "";
        for(String s:users){
            String[] s1=s.split("===");
            if(s1.length==2){
                user=s1[1];
            }else {
                continue;
            }
            online_users+="\t"+user+"\n";
        }
        System.out.println(online_users);
        return online_users;
    }

    private void offlineUser(SelectionKey sk) throws IOException {
        SocketChannel socketChannel = (SocketChannel) sk.channel();
        for (String s:users){
            String[] s1 = s.split("===");
            if(s1.length==2){
                String user_name=s1[1];
                if(s.contains(socketChannel.getRemoteAddress().toString())){
                    users.remove(s);
                    String message = "用户: " + user_name + " 下线了, 拜拜";
                    brodcast(socketChannel,message);
                }
            }else {
                continue;
            }
        }

    }

    //群聊
    public void brodcast(SocketChannel except, String content) throws IOException{
        for (SelectionKey key: selector.keys()) {
            Channel targetchannel = key.channel();
            System.out.println("broadcast write:" + content);
            if(targetchannel instanceof SocketChannel && targetchannel != except) {
                SocketChannel channel = (SocketChannel) key.channel();
                write(channel, content);
            }
        }
    }
    private void write(SocketChannel channel, String content) throws IOException, ClosedChannelException {
        sendbuf.clear();
        sendbuf.put(content.getBytes());
        sendbuf.flip();
        channel.write(sendbuf);
        //注册读操作 下一次进行读
        channel.register(selector, SelectionKey.OP_READ);
    }




}
