package com.wuswoo.easypay.service.callback;

import com.wuswoo.easypay.service.model.NotifySchedule;
import com.wuswoo.easypay.service.model.RefundResult;

import java.util.List;

/**
 * Created by wuxinjun on 16/9/13.
 */
public interface ICallbackService {
    /**
     * 支付回调业务系统
     * @param notifySchedule
     * @return
     */
    boolean updatePaymentStatus(NotifySchedule notifySchedule);

}
