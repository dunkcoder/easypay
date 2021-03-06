package com.wuswoo.easypay.service.callback.impl;

import com.alibaba.fastjson.JSON;
import com.wuswoo.easypay.service.callback.ICallbackTask;
import com.wuswoo.easypay.service.mapper.NotifyFailedLogMapper;
import com.wuswoo.easypay.service.mapper.NotifyScheduleMapper;
import com.wuswoo.easypay.service.mapper.PaymentExtMapper;
import com.wuswoo.easypay.service.mapper.PaymentMapper;
import com.wuswoo.easypay.service.misc.PaymentEvent;
import com.wuswoo.easypay.service.misc.rabbitmq.MQSender;
import com.wuswoo.easypay.service.model.NotifySchedule;
import com.wuswoo.easypay.service.model.Payment;
import com.wuswoo.easypay.service.model.PaymentExample;
import com.wuswoo.easypay.service.repository.INotifyScheduleService;
import com.wuswoo.easypay.service.repository.IPaymentDBService;
import com.wuswoo.easypay.service.repository.IPaymentService;
import com.wuswoo.easypay.service.util.PayConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Created by wuxinjun on 16/11/30.
 */

public class RabbitmqCallbackTask implements ICallbackTask {
    private static final Logger logger = LogManager.getLogger(RabbitmqCallbackTask.class);

    private NotifySchedule notifySchedule;

    @Autowired
    private IPaymentService paymentService;

    @Autowired
    private INotifyScheduleService notifyScheduleService;

    @Autowired
    private IPaymentDBService paymentDBService;

    @Resource(name="appEventMQSender")
    MQSender paymentEventMQSender;

    @Value("${easypay.callback.appId}")
    String appId;


    @Override
    public void setNotifySchedule(NotifySchedule notifySchedule) {
        this.notifySchedule = notifySchedule;
    }

    @Override
    public void run() {
        logger.info("start to notify rabbitmq server: {}", JSON.toJSONString(notifySchedule));
        //check paymentCode existed or not
        try {
            Payment payment = paymentDBService.getPaymentByPaymentCode(notifySchedule.getPaymentCode());
        } catch (Exception ex) {
            logger.warn("找不到paymentCode: {}", notifySchedule.getPaymentCode());
            return;
        }
        //check notifyschedule existed status
        NotifySchedule existedNotifySchedule = notifyScheduleService.getNotifySchedule(
            notifySchedule.getTradeId(),
            notifySchedule.getTradeType(),
            notifySchedule.getPaymentCode(),
            notifySchedule.getPlatformId()
        );
        //notifySchedule.setNotifyContent(JSON.toJSONString(notifySchedule));
        if (existedNotifySchedule != null) {
            notifySchedule.setId(existedNotifySchedule.getId());
            notifySchedule.setNotifyCount(existedNotifySchedule.getNotifyCount());
            //如果已经通知成功,直接返回
            if (notifySchedule.getNotifyStatus() != null &&
                PayConstant.NotifyResultStatus.SUCCESS.byteValue() == existedNotifySchedule.getNotifyStatus().byteValue()) {
                logger.info("already notify to rabbitmq server.");
                return;
            }
        }
        //回调处理
        try {
            PaymentEvent event = new PaymentEvent();
            Integer appCode = 1;
            if (!StringUtils.isBlank(appId)){
                appCode = Integer.parseInt(appId);
            }
            if (notifySchedule.getTradeType().byteValue() == PayConstant.NotifyResultType.PAYMENT.byteValue()) {
                event.setEventType(PayConstant.NotifyEvent.PAYMENT);
            }
            if (notifySchedule.getTradeType().byteValue() == PayConstant.NotifyResultType.REFUND.byteValue()) {
                event.setEventType(PayConstant.NotifyEvent.REFUND);
            }
            event.setAppCode(appCode);
            event.setContent((JSON)JSON.toJSON(notifySchedule));
            sendNotify(event);
            logger.info("send message to rabbitmq success.");
            //更新数据库
            updateNotifySchedule(PayConstant.NotifyResultStatus.SUCCESS.byteValue(), "");
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("回调业务系统出现错误", ex);
            updateNotifySchedule(PayConstant.NotifyResultStatus.FAIL.byteValue(), ex.getMessage());
        }
    }

    public void sendNotify(PaymentEvent event)
    {
        paymentEventMQSender.sendAsync(event.getEventType(), event);
    }

    private void updateNotifySchedule(Byte status, String message) {
        //更新数据库
        if (notifySchedule.getId() != null && notifySchedule.getId() > 0) {
            NotifySchedule newNs = new NotifySchedule();
            newNs.setId(notifySchedule.getId());
            newNs.setNotifyStatus(status);
            newNs.setUpdatedTime(new Date());
            newNs.setNotifyReturn(message);
            newNs.setNotifyCount((byte)(notifySchedule.getNotifyCount() + 1));
            paymentService.updateNotifySchedule(newNs);
        } else {
            notifySchedule.setNotifyStatus(status);
            notifySchedule.setUpdatedTime(new Date());
            notifySchedule.setNotifyReturn(message);
            paymentService.saveNotifySchedule(notifySchedule);
        }
    }
}
