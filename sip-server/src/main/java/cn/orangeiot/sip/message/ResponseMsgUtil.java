package cn.orangeiot.sip.message;

import cn.orangeiot.common.options.SendOptions;
import cn.orangeiot.reg.user.UserAddr;
import cn.orangeiot.sip.SipVertxFactory;
import cn.orangeiot.sip.constant.SipOptions;
import cn.orangeiot.sip.handler.PorcessHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sip.SipFactory;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2018-02-02
 */
public class ResponseMsgUtil implements UserAddr {

    private static Logger logger = LogManager.getLogger(ResponseMsgUtil.class);

    /**
     * @Description send Mesaage
     * @author zhang bo
     * @date 18-2-2
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public static void sendMessage(String username, String msg, SipOptions sipOptions) {
        SipVertxFactory.getVertx().eventBus().send(UserAddr.class.getName() + GET_REGISTER_USER,
                username, SendOptions.getInstance(), (AsyncResult<Message<String>> as) -> {
                    if (as.failed()) {
                        as.cause().printStackTrace();
                    } else {
                        if (Objects.nonNull(as.result().body())) {
                            logger.info("==ResponseMsgUtil==sendMessage send  username->\n" + username);
                            logger.info("==ResponseMsgUtil==sendMessage send  package->\n" + msg);
                            if (sipOptions == SipOptions.UDP) {
                                String[] address=as.result().body().split(":");
                                SocketAddress socket = new SocketAddressImpl(Integer.parseInt(address[1]),address[0]);
                                logger.info("==ResponseMsgUtil==sendMessage send  UDP host -> {},port -> {}->\n", socket.host(), socket.port());
                                SipVertxFactory.getSocketInstance().send(msg, socket.port(), socket.host(),rs -> {
                                    if (rs.failed()) {
                                        rs.cause().printStackTrace();
                                    } else {
                                        logger.info("send success ->" + rs.succeeded());
                                        if (rs.failed())
                                            reSend(msg, username);
                                    }
                                });
                            } else {
//                                NetSocket socket = (NetSocket) as.result().body();
//                                socket.write(msg);
//                                if (socket.writeQueueFull()) {
//                                    socket.pause();
//                                    socket.drainHandler(done -> socket.resume());
//                                }
                            }
                        }
                    }
                });

    }


    /**
     * @param msg 消息
     * @Description 重发消息
     * @author zhang bo
     * @date 18-2-27
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public static void reSend(String msg, String username) {
        logger.info("==ResponseMsgUtil==reSend==params -> msg = {} , socket = {}", msg, username);
        //todo 重发消息
        AtomicInteger atomicInteger = new AtomicInteger(0);
        SipVertxFactory.getVertx().setPeriodic(SipVertxFactory.getConfig().getLong("intervalTimes"), rs -> {
            SipVertxFactory.getVertx().eventBus().send(UserAddr.class.getName() + GET_REGISTER_USER,
                    username, SendOptions.getInstance(), (AsyncResult<Message<String>> as) -> {
                        if (as.failed()) {
                            as.cause().printStackTrace();
                        } else {
                            atomicInteger.getAndIncrement();//原子自增
                            if (atomicInteger.intValue() == SipVertxFactory.getConfig().getInteger("maxTimes")) {//达到重发次数
                                SipVertxFactory.getVertx().cancelTimer(rs);//取消周期定时
                            }

                            if (Objects.nonNull(as.result().body())) {
                                String[] address=as.result().body().split(":");
                                SocketAddress socket = new SocketAddressImpl(Integer.parseInt(address[1]),address[0]);
                                SipVertxFactory.getSocketInstance().send(msg, socket.port(), socket.host(), ars -> {
                                    if (ars.failed())
                                        ars.cause().printStackTrace();
                                    else
                                        SipVertxFactory.getVertx().cancelTimer(rs);//取消周期定时
                                });
                            }
                        }
                    });
        });
    }

}