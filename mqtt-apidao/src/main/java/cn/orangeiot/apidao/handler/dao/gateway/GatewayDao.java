package cn.orangeiot.apidao.handler.dao.gateway;

import cn.orangeiot.apidao.client.MongoClient;
import cn.orangeiot.apidao.client.RedisClient;
import cn.orangeiot.apidao.conf.RedisKeyConf;
import cn.orangeiot.apidao.handler.dao.file.FileDao;
import cn.orangeiot.common.genera.ErrorType;
import cn.orangeiot.common.options.SendOptions;
import cn.orangeiot.reg.memenet.MemenetAddr;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.mongo.WriteOption;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import scala.util.parsing.json.JSONArray;
import scala.util.parsing.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2018-01-04
 */
public class GatewayDao {
    private static Logger logger = LogManager.getLogger(GatewayDao.class);


    /**
     * @Description 用户绑定网关
     * @author zhang bo
     * @date 18-1-4
     * @version 1.0
     */
    public void onbindGatewayByUser(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                .put("isAdmin", 1), new JsonObject().put("_id", 1).put("uid", 1).put("adminuid", 1).put("adminName", 1)
                .put("adminNickname", 1), as -> {
            if (as.failed()) {
                as.cause().printStackTrace();
                message.reply(null);
            } else {
                if (Objects.nonNull(as.result())) {//存在管理员
                    if (Objects.nonNull(as.result().getValue("uid")) && as.result().getString("uid")
                            .equals(message.body().getString("uid"))) {
                        message.reply(new JsonObject(), new DeliveryOptions().addHeader("code",
                                String.valueOf(ErrorType.HAVE_ADMIN_BY_GATEWAY.getKey())).addHeader("msg", ErrorType.HAVE_ADMIN_BY_GATEWAY.getValue()));
                        return;
                    }
                    message.reply(new JsonObject(), new DeliveryOptions().addHeader("code",
                            String.valueOf(ErrorType.NOTIFY_ADMIN_BY_GATEWAY.getKey())).addHeader("msg", ErrorType.NOTIFY_ADMIN_BY_GATEWAY.getValue()));
                    RedisClient.client.hget(RedisKeyConf.USER_INFO, message.body().getString("uid"), userResult -> {
                        if (userResult.failed()) {
                            userResult.cause().printStackTrace();
                            message.reply(null);
                        } else {
                            // 加入审批列表
                            if (Objects.nonNull(userResult.result())) {
                                JsonObject jsonObject = new JsonObject(userResult.result());
                                MongoClient.client.findOne("kdsApprovalList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                                        .put("uid", message.body().getString("uid")).put("type", 1)
                                        .put("status", 1), new JsonObject().put("_id", 1), approvalResult -> {
                                    if (approvalResult.failed()) {
                                        approvalResult.cause().printStackTrace();
                                    } else {
                                        //处理是否是催促
                                        if (!Objects.nonNull(approvalResult.result())) {
                                            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                            MongoClient.client.save("kdsApprovalList", new JsonObject()
                                                    .put("deviceSN", message.body().getString("devuuid")).put("uid", jsonObject.getString("_id"))
                                                    .put("deviceNickName", message.body().getString("devuuid")).put("username", jsonObject.getString("username"))
                                                    .put("userNickname", jsonObject.getString("username")).put("approvaluid", as.result().getString("adminuid"))
                                                    .put("approvalName", as.result().getString("adminName")).put("approvalNickname", as.result().getString("adminNickname"))
                                                    .put("requestTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                                    .put("type", 1).put("approvalTime", time).put("status", 1), rs -> {//status 1 有效
                                                if (rs.failed()) rs.cause().printStackTrace();
                                            });
                                        }
                                    }
                                });

                            }
                        }
                    });
                } else {//绑定网关(角色管理员)
                    RedisClient.client.hget(RedisKeyConf.USER_INFO, message.body().getString("uid"), userResult -> {
                        if (userResult.failed()) {
                            userResult.cause().printStackTrace();
                            message.reply(null);
                        } else {
                            if (Objects.nonNull(userResult.result())) {
                                JsonObject jsonObject = new JsonObject(userResult.result());
                                MongoClient.client.save("kdsGatewayDeviceList", new JsonObject()
                                        .put("deviceSN", message.body().getString("devuuid")).put("uid", jsonObject.getString("_id"))
                                        .put("deviceNickName", message.body().getString("devuuid")).put("username", jsonObject.getString("username"))
                                        .put("userNickname", jsonObject.getString("username")).put("adminuid", jsonObject.getString("_id"))
                                        .put("adminName", jsonObject.getString("username")).put("adminNickname", jsonObject.getString("username")).put("isAdmin", 1)
                                        .put("bindTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                        .put("userid", jsonObject.getLong("userid")), rs -> {
                                    if (rs.failed())
                                        message.reply(null);
                                    else
                                        message.reply(new JsonObject());
                                });
                            } else {
                                message.reply(null);
                            }
                        }
                    });
                }
            }
        });
    }


    /**
     * @Description 获取网关管理员
     * @author zhang bo
     * @date 18-1-5
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void onGetGatewayAdmin(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                .put("isAdmin", 1), new JsonObject().put("_id", 1).put("deviceNickName", 1).put("deviceSN", 1)
                .put("adminuid", 1), as -> {
            if (as.failed()) {
                as.cause().printStackTrace();
            } else {
                if (Objects.nonNull(as.result())) {
                    RedisClient.client.hget(RedisKeyConf.USER_INFO, message.body().getString("uid"), rs -> {
                        if (rs.failed()) {
                            as.cause().printStackTrace();
                        } else {
                            if (Objects.nonNull(rs.result()))
                                message.reply(as.result().put("requestNickName", new JsonObject(rs.result()).getString("nickName")));
                            else
                                message.reply(null);
                        }
                    });

                } else {
                    message.reply(null);
                }
            }
        });
    }


    /**
     * @Description 审批普通用户绑定网关
     * @author zhang bo
     * @date 18-1-8
     * @version 1.0
     */
    public void onApprovalBindGateway(Message<JsonObject> message) {
        MongoClient.client.updateCollection("kdsApprovalList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                .put("uid", message.body().getString("requestuid")), new JsonObject().put("$set", new JsonObject()
                .put("type", message.body().getInteger("type")).put("approvalTime"
                        , LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
            } else {
                message.reply(new JsonObject().put("type", message.body().getInteger("type")));//审批状态
            }
        });
        if (message.body().getInteger("type") == 2) {//审批通过
            RedisClient.client.hmget(RedisKeyConf.USER_INFO, new ArrayList<String>() {{
                add(message.body().getString("requestuid"));
                add(message.body().getString("uid"));
            }}, as -> {
                if (as.failed()) {
                    as.cause().printStackTrace();
                } else {
                    JsonObject reqUser = new JsonObject(as.result().getList().get(0).toString());//请求用户
                    JsonObject adminUser = new JsonObject(as.result().getList().get(1).toString());//管理员用户
                    MongoClient.client.save("kdsGatewayDeviceList", new JsonObject()
                            .put("deviceSN", message.body().getString("devuuid")).put("uid", reqUser.getString("_id"))
                            .put("deviceNickName", message.body().getString("devuuid")).put("username", reqUser.getString("username"))
                            .put("userNickname", reqUser.getString("nickName")).put("adminuid", adminUser.getString("_id"))
                            .put("adminName", adminUser.getString("username")).put("adminNickname", adminUser.getString("nickName")).put("isAdmin", 2)
                            .put("bindTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .put("userid", reqUser.getLong("userid")), rs -> {
                        if (rs.failed()) rs.cause().printStackTrace();
                    });


                }
            });
        }
    }


    /**
     * @Description 获取网关绑定列表
     * @author zhang bo
     * @date 18-1-10
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void onGetGatewayBindList(Message<JsonObject> message) {
        MongoClient.client.findWithOptions("kdsGatewayDeviceList", new JsonObject().put("uid",
                message.body().getString("uid")), new FindOptions().setFields(new JsonObject().put("deviceSN", 1)
                .put("deviceNickName", 1).put("adminuid", 1)), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                List<JsonObject> resultList = rs.result().stream().map(e -> {
                    if (e.getString("adminuid").equals(message.body().getString("uid")))
                        e.put("isAdmin", 1);//管理员
                    else
                        e.put("isAdmin", 2);//普通用户
                    e.remove("adminuid");
                    return e;
                }).collect(Collectors.toList());
                message.reply(new JsonArray(resultList));
            }
        });
    }


    /**
     * @Description 获取审批列表
     * @author zhang bo
     * @date 18-1-10
     * @version 1.0
     */
    public void onApprovalList(Message<JsonObject> message) {
        MongoClient.client.findWithOptions("kdsApprovalList", new JsonObject().put("approvaluid",
                message.body().getString("uid")).put("type", 1).put("status", 1), new FindOptions().setFields(new JsonObject().put("deviceSN", 1)
                .put("deviceNickName", 1).put("username", 1).put("userNickname", 1)
                .put("uid", 1).put("requestTime", 1)), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                message.reply(new JsonArray(rs.result()));
            }
        });
    }

    /**
     * @Description 获取用户信息
     * @author zhang bo
     * @date 18-1-13
     * @version 1.0
     */
    public void onGetUserInfo(Message<JsonObject> message) {
        RedisClient.client.hget(RedisKeyConf.USER_INFO, message.body().getString("uid"), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                message.reply(new JsonObject(rs.result()));
            }
        });
    }


    /**
     * @Description 修改网关的域
     * @author zhang bo
     * @date 18-1-13
     * @version 1.0
     */
    public void onupdateGWDomain(Message<JsonObject> message) {
        MongoClient.client.updateCollectionWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devicesn"))
                , new JsonObject().put("$set", new JsonObject().put("domain", message.body().getString("domain")))
                , new UpdateOptions().setUpsert(false), rs -> {
                    if (rs.failed()) rs.cause().printStackTrace();
                });

        if (Objects.nonNull(message.body().getValue("type"))) {//綁定網關失敗
            if (StringUtils.isNotBlank(message.body().getString("userid")))
                MongoClient.client.removeDocument("kdsGatewayDeviceList", new JsonObject()
                        .put("deviceSN", message.body().getString("devicesn")).put("userid", Long.parseLong(message.body().getString("userid"))), rs -> {
                    if (rs.failed())
                        rs.cause().printStackTrace();
                });
            else
                MongoClient.client.removeDocument("kdsGatewayDeviceList", new JsonObject()
                        .put("deviceSN", message.body().getString("devicesn")).put("uid", message.body().getString("uid")), rs -> {
                    if (rs.failed())
                        rs.cause().printStackTrace();
                });
        }
    }


    /**
     * @Description 解绑网关
     * @author zhang bo
     * @date 18-1-15
     * @version 1.0
     */
    public void onUnbindGateway(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                .put("adminuid", message.body().getString("uid")), new JsonObject().put("_id", 1), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                if (Objects.nonNull(rs.result())) {//管理员解绑
                    message.reply(new JsonObject(), new DeliveryOptions().addHeader("mult", "true"));
                } else {
                    message.reply(new JsonObject(), new DeliveryOptions().addHeader("mult", "false"));
                }
            }
        });
    }


    /**
     * @Description 获取mimi网用户userid
     * @author zhang bo
     * @date 18-1-15
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void onGetUserIdList(Message<JsonObject> message) {
        if (message.body().getString("mult").equals("true")) {
            MongoClient.client.findWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                    , message.body().getString("devuuid")), new FindOptions().setFields(new JsonObject()
                    .put("userid", 1).put("_id", 0)), rs -> {
                if (rs.failed()) {
                    rs.cause().printStackTrace();
                    message.reply(null);
                } else {
                    message.reply(new JsonArray(rs.result()));

//                    MongoClient.client.updateCollectionWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
//                            , message.body().getString("devuuid")).put("adminuid", message.body().getString("uid"))
//                            , new JsonObject().put("$set", new JsonObject().put("status", 2)),
//                            new UpdateOptions().setUpsert(false).setMulti(true), as -> {//status 2失效
//                                if (as.failed()) {
//                                    as.cause().printStackTrace();
//                                    message.reply(null);
//                                } else {
//                                    message.reply(new JsonObject(), new DeliveryOptions().addHeader("mult", "false"));
//                                }
//                            });

                    MongoClient.client.removeDocuments("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                            , message.body().getString("devuuid")).put("adminuid", message.body().getString("uid"))
                            , as -> {//status 2失效
                                if (as.failed())
                                    logger.error(as.cause().getMessage(), as);
                            });

                    //未审批的列表失效
                    MongoClient.client.updateCollectionWithOptions("kdsApprovalList", new JsonObject().put("deviceSN"
                            , message.body().getString("devuuid")).put("type", 1)
                            , new JsonObject().put("$set", new JsonObject().put("status", 2)//status 改为失效状态
                                    .put("failureTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                            , new UpdateOptions().setMulti(true).setUpsert(true), as -> {
                                if (as.failed())
                                    logger.error(as.cause().getMessage(), as);

                            });

                    //插入綁定網關的歷史記錄
                    MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                            , message.body().getString("devuuid")).put("adminuid", message.body().getString("uid"))
                            , new JsonObject().put("userid", 1).put("_id", 0).put("deviceList", 1).put("bindTime", 1), ars -> {
                                if (ars.failed()) {
                                    logger.error(ars.cause().getMessage(), ars);
                                } else {
                                    MongoClient.client.insert("kdsbindGWHistoryRecord", new JsonObject().put("deviceSN"
                                            , message.body().getString("devuuid")).put("deviceList",
                                            Objects.nonNull(ars.result().getValue("deviceList")) ?
                                                    ars.result().getJsonArray("deviceList") : new JsonArray())
                                                    .put("adminuid", message.body().getString("uid"))
                                                    .put("bindTime", ars.result().getString("bindTime"))
                                            , as -> {
                                                if (as.failed())
                                                    logger.error(as.cause().getMessage(), as);
                                            });
                                }
                            });
                }
            });
        } else {
            MongoClient.client.findWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                    , message.body().getString("devuuid")).put("uid", message.body().getString("uid"))
                    , new FindOptions().setFields(new JsonObject()
                            .put("userid", 1).put("_id", 0)), rs -> {
                        if (rs.failed()) {
                            rs.cause().printStackTrace();
                            message.reply(null);
                        } else {
                            message.reply(new JsonArray(rs.result()));

                            MongoClient.client.removeDocument("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                                    , message.body().getString("devuuid")).put("uid", message.body().getString("uid"))
                                    , as -> {
                                        if (as.failed())
                                            logger.error(as.cause().getMessage(), as);
                                    });
                        }
                    });
        }
    }


    /**
     * @Description 删除网关用户
     * @author zhang bo
     * @date 18-1-15
     * @version 1.0
     */
    public void onDelGatewayUser(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                        .put("adminuid", message.body().getString("uid")).put("uid", message.body().getString("uid"))
                , new JsonObject().put("_id", 0).put("userid", 1), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                        message.reply(null);
                    } else {
                        if (Objects.nonNull(rs.result())) {//是否是设备管理员
                            MongoClient.client.removeDocument("kdsGatewayDeviceList",
                                    new JsonObject().put("_id", new JsonObject().put("$oid", message.body().getString("_id"))), as -> {
                                        if (as.failed()) {
                                            as.cause().printStackTrace();
                                            message.reply(null);
                                        } else {
                                            message.reply(rs.result());
                                        }
                                    });
                        } else {
                            message.reply(null);
                        }
                    }
                });
    }


    /**
     * @Description 获取网关普通用户
     * @author zhang bo
     * @date 18-1-15
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void onGetGatewayUserList(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN", message.body().getString("devuuid"))
                .put("adminuid", message.body().getString("uid")), new JsonObject().put("_id", 0), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                if (Objects.nonNull(rs.result())) {
                    MongoClient.client.findWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN"
                            , message.body().getString("devuuid")), new FindOptions().setFields(new JsonObject()
                            .put("uid", 1).put("username", 1).put("userNickname", 1).put("_id", 1)), as -> {
                        if (as.failed()) {
                            as.cause().printStackTrace();
                            message.reply(null);
                        } else {
                            message.reply(new JsonArray(as.result().stream().filter(
                                    e -> !e.getString("uid").equals(message.body().getString("uid"))).collect(Collectors.toList())));
                        }
                    });
                } else {
                    message.reply(null);
                }
            }
        });

    }


    /**
     * @Description 获取网关管理员
     * @author zhang bo
     * @date 18-1-16
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void onGetGatewayAdminByuid(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                message.body().getString("gwId")).put("isAdmin", 1), new JsonObject().put("uid", 1)
                .put("_id", 0), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
                message.reply(null);
            } else {
                if (Objects.nonNull(rs.result()))
                    message.reply(rs.result());
                else
                    message.reply(null);
            }
        });
    }


    /**
     * @Description 設備上線
     * @author zhang bo
     * @date 18-3-22
     * @version 1.0
     */
    public void deviceOnline(Message<JsonObject> message) {
        MongoClient.client.updateCollection("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                message.body().getString("clientId").split(":")[1]).put("deviceList.deviceId", new JsonObject()
                        .put("$in", new JsonArray().add(message.body().getString("deviceId")))),
                new JsonObject().put("$pull", new JsonObject().put("deviceList"
                        , new JsonObject().put("deviceId", message.body().getString("deviceId")))), as -> {
                    if (as.failed()) {
                        logger.error(as.cause().getMessage(), as);
                    } else {
                        message.body().getJsonObject("eventparams").put("deviceId", message.body().getString("deviceId"))
                                .put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                        MongoClient.client.updateCollectionWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                                message.body().getString("clientId").split(":")[1]),
                                new JsonObject().put("$addToSet", new JsonObject().put("deviceList"
                                        , message.body().getJsonObject("eventparams")))
                                , new UpdateOptions().setUpsert(false).setMulti(true), rs -> {
                                    if (rs.failed()) {
                                        rs.cause().printStackTrace();
                                    }
                                });
                    }
                });
    }


    /**
     * @Description 設備下線
     * @author zhang bo
     * @date 18-3-22
     * @version 1.0
     */
    public void deviceOffline(Message<JsonObject> message) {
        MongoClient.client.updateCollectionWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                message.body().getString("clientId").split(":")[1]).put("deviceList.deviceId", new JsonObject()
                        .put("$in", new JsonArray().add(message.body().getString("deviceId")))),
                new JsonObject().put("$set", new JsonObject().put("deviceList.$.event_str"
                        , message.body().getJsonObject("eventparams").getString("event_str"))
                        .put("deviceList.$.time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))//下线狀態
                , new UpdateOptions().setUpsert(false).setMulti(true), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                    }
                });
    }


    /**
     * @Description 設備删除
     * @author zhang bo
     * @date 18-3-22
     * @version 1.0
     */
    public void deviceDelete(Message<JsonObject> message) {
        MongoClient.client.updateCollectionWithOptions("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                message.body().getString("clientId").split(":")[1]).put("deviceList.deviceId", new JsonObject()
                        .put("$in", new JsonArray().add(message.body().getString("deviceId")))),
                new JsonObject().put("$set", new JsonObject().put("deviceList.$.event_str"
                        , message.body().getJsonObject("eventparams").getString("event_str"))
                        .put("deviceList.$.time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))//刪除狀態
                , new UpdateOptions().setUpsert(false).setMulti(true), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                    }
                });
    }


    /**
     * @Description 獲取設備列表
     * @author zhang bo
     * @date 18-3-22
     * @version 1.0
     */
    public void getDeviceList(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsGatewayDeviceList", new JsonObject().put("deviceSN",
                message.body().getString("clientId").split(":")[1])
                        .put("deviceList.event_str", new JsonObject().put("$in", new JsonArray()
                                .add("online").add("offline"))), new JsonObject().put("deviceList", 1).put("_id", 0),
                (AsyncResult<JsonObject> rs) -> {// 1 上线, 2 下线
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                    } else {
                        if (Objects.nonNull(rs.result().getValue("deviceList"))) {
                            message.reply(new JsonObject().put("deviceList",
                                    new JsonArray(rs.result().getJsonArray("deviceList").stream().map(e -> {
                                        JsonObject resultJsonObject = new JsonObject(e.toString());
                                        resultJsonObject.remove("time");
                                        return resultJsonObject;
                                    }).collect(Collectors.toList()))));
                        } else {
                            message.reply(new JsonObject().put("deviceList", new JsonArray()));
                        }
                    }
                });
    }


    /**
     * @Description 開門記錄
     * @author zhang bo
     * @date 18-7-20
     * @version 1.0
     */
    public void EventOpenLock(Message<JsonObject> message) {
        MongoClient.client.findOne("kdsDeviceList", new JsonObject().put("lockName", message.body().getString("deviceId"))
                , new JsonObject().put("infoList", 1).put("_id", 0), rs -> {//獲取鎖映射打昵稱
                    if (rs.failed()) {
                        logger.error(rs.cause().getMessage(), rs);
                    } else {
                        String nickName = "";
                        if (Objects.nonNull(rs.result()) && Objects.nonNull(rs.result().getValue("infoList")) && rs.result().getJsonArray("infoList").size() > 0) //鎖編號名稱
                            nickName = rs.result().getJsonArray("infoList").stream().filter(e -> new JsonObject(e.toString()).getString("num").equals(
                                    String.valueOf(message.body().getInteger("userID")))).map(e -> new JsonObject(e.toString()).getString("numNickname")).findFirst().orElse("");
                        else
                            nickName = String.valueOf(message.body().getJsonObject("eventparams").getInteger("userID"));
                        MongoClient.client.updateCollectionWithOptions("kdsOpenLockList", new JsonObject()
                                        .put("lockName", message.body().getString("deviceId")).put("user_num", String.valueOf(message.body()
                                                .getJsonObject("eventparams").getInteger("userID"))).put("medium", message.body().getString("gwId"))
                                        .put("open_type", String.valueOf(message.body().getJsonObject("eventparams").getInteger("eventsource")))
                                        .put("open_time", message.body().getString("timestamp"))
                                , new JsonObject().put("$set", new JsonObject()
                                        .put("open_time", message.body().getString("timestamp"))
                                        .put("open_type", String.valueOf(message.body().getJsonObject("eventparams").getInteger("eventsource")))
                                        .put("medium", message.body().getString("gwId"))
                                        .put("lockName", message.body().getString("deviceId"))
                                        .put("user_num", String.valueOf(message.body().getJsonObject("eventparams").getInteger("userID")))
                                        .put("nickName", nickName))
                                , new UpdateOptions().setMulti(false).setUpsert(true), res -> {
                                    if (res.failed())
                                        logger.error(res.cause().getMessage(), res);
                                });
                    }
                });
    }


    /**
     * @Description 查詢開鎖記錄
     * @author zhang bo
     * @date 18-7-20
     * @version 1.0
     */
    public void selectOpenLock(Message<JsonObject> message) {
        MongoClient.client.findWithOptions("kdsGatewayDeviceList", new JsonObject().put("devuuid", message.body().getString("deviceSN"))
                        .put("adminuid", new JsonObject().put("$exists", true)).put("uid", message.body().getString("uid")),
                new FindOptions().setFields(new JsonObject().put("adminuid", 1).put("_id", 0).put("uname", 1)
                        .put("deviceSN", 1)), ars -> {
                    if (ars.failed()) {
                        ars.cause().printStackTrace();
                        message.reply(null);
                    } else {
                        JsonObject paramsJsonObject = new JsonObject();
                        // 根据不同权限查询记录
                        if (ars.result().size() > 0 && ars.result().get(0).getString("adminuid").equals(message.body().getString("uid"))) {

                            MongoClient.client.findWithOptions("kdsbindGWHistoryRecord", new JsonObject()
                                            .put("deviceList.deviceId", message.body().getString("deviceId")),
                                    new FindOptions().setFields(new JsonObject().put("adminuid", 1).put("_id", 0)
                                            .put("deviceSN", 1)), rs -> {
                                        if (rs.failed()) {
                                            logger.error(rs.cause().getMessage(), rs);
                                        } else {
                                            if (rs.result().size() > 0) {
                                                List<String> deviceList = rs.result().stream().filter(e -> e.getString("adminuid").equals(message.body().getString("uid"))).map(e -> e.getString("deviceSN"))
                                                        .distinct().collect(Collectors.toList());
                                                paramsJsonObject.put("lockName", message.body().getString("deviceId"))
                                                        .put("medium", new JsonObject().put("$in", new JsonArray(deviceList)));
                                            } else {
                                                paramsJsonObject.put("lockName", message.body().getString("deviceId"))
                                                        .put("medium", message.body().getString("devuuid"));
                                            }
                                            selectLockRecord(message, paramsJsonObject);
                                        }
                                    });
                        } else {//普通用戶
                            paramsJsonObject.put("lockName", message.body().getString("deviceId"))
                                    .put("uname", message.body().getString("uid"));
                            selectLockRecord(message, paramsJsonObject);
                        }

                    }
                });
    }


    /**
     * @Description 所記錄
     * @author zhang bo
     * @date 18-7-20
     * @version 1.0
     */
    public void selectLockRecord(Message<JsonObject> message, JsonObject paramsJsonObject) {
        int page = message.body().getInteger("page");
        int pageNum = message.body().getInteger("pageNum");
        MongoClient.client.findWithOptions("kdsOpenLockList", paramsJsonObject,
                new FindOptions().setSort(new JsonObject().put("open_time", -1))
                        .setLimit(pageNum).setSkip((page - 1) * pageNum)
                        .setFields(new JsonObject().put("_id", 0).put("uid", 0).put("medium", 0)), rs -> {
                    if (rs.failed()) {
                        logger.error(rs.cause().getMessage(), rs);
                        message.reply(null);
                    } else {
                        if (Objects.nonNull(rs.result())) {
                            message.reply(new JsonArray(rs.result()));
                        } else
                            message.reply(new JsonArray());
                    }
                });
    }

}