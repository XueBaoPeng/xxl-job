package com.xxl.job.core.thread;

import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.rpc.netcom.NetComClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by xuxueli on 16/7/22.
 */
public class TriggerCallbackThread {
    private static Logger logger = LoggerFactory.getLogger(TriggerCallbackThread.class);

    private static TriggerCallbackThread instance = new TriggerCallbackThread();
    public static TriggerCallbackThread getInstance(){
        return instance;
    }

    private LinkedBlockingQueue<HandleCallbackParam> callBackQueue = new LinkedBlockingQueue<HandleCallbackParam>();

    private Thread triggerCallbackThread;
    private boolean toStop = false;
    public void start() {
        triggerCallbackThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while(!toStop){
                    try {
                        HandleCallbackParam callback = getInstance().callBackQueue.take();
                        if (callback != null) {

                            // valid
                            if (XxlJobExecutor.adminAddresses==null || XxlJobExecutor.adminAddresses.trim().length()==0) {
                                logger.warn(">>>>>>>>>>>> xxl-job callback fail, adminAddresses is null.");
                                continue;
                            }

                            // callback list param
                            List<HandleCallbackParam> callbackParamList = new ArrayList<HandleCallbackParam>();
                            int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);
                            callbackParamList.add(callback);

                            // callback, will retry if error
                            try {

                                ReturnT<String> callbackResult = null;
                                for (String addressUrl: XxlJobExecutor.adminAddresses.split(",")) {
                                    String apiUrl = addressUrl.concat("/api");

                                    AdminBiz adminBiz = (AdminBiz) new NetComClientProxy(AdminBiz.class, apiUrl).getObject();
                                    callbackResult = adminBiz.callback(callbackParamList);
                                    if (callbackResult!=null && ReturnT.SUCCESS_CODE == callbackResult.getCode()) {
                                        callbackResult = ReturnT.SUCCESS;
                                        break;
                                    }
                                }

                                logger.info(">>>>>>>>>>> xxl-job callback, callbackParamList:{}, callbackResult:{}", new Object[]{callbackParamList, callbackResult});
                            } catch (Exception e) {
                                logger.error(">>>>>>>>>>> xxl-job TriggerCallbackThread Exception:", e);
                                //getInstance().callBackQueue.addAll(callbackParamList);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }
            }
        });
        triggerCallbackThread.setDaemon(true);
        triggerCallbackThread.start();
    }
    public void toStop(){
        toStop = true;
    }

    public static void pushCallBack(HandleCallbackParam callback){
        getInstance().callBackQueue.add(callback);
        logger.debug(">>>>>>>>>>> xxl-job, push callback request, logId:{}", callback.getLogId());
    }

}
