package cn.orangeiot.reg;


import cn.orangeiot.reg.adminlock.AdminlockAddr;
import cn.orangeiot.reg.file.FileAddr;
import cn.orangeiot.reg.message.MessageAddr;
import cn.orangeiot.reg.publish.PublishAddr;
import cn.orangeiot.reg.user.UserAddr;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2017-12-12
 */
public interface EventbusAddr extends PublishAddr,MessageAddr,UserAddr,FileAddr,AdminlockAddr{
}