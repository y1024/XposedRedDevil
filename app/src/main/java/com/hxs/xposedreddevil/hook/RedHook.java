package com.hxs.xposedreddevil.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hxs.xposedreddevil.contentprovider.PropertiesUtils;
import com.hxs.xposedreddevil.model.DBean;
import com.hxs.xposedreddevil.model.MsgsBean;
import com.hxs.xposedreddevil.utils.MessageEvent;
import com.hxs.xposedreddevil.utils.PinYinUtils;
import com.hxs.xposedreddevil.utils.PlaySoundUtils;
import com.hxs.xposedreddevil.utils.PushUtils;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import fr.arnaudguyon.xmltojsonlib.XmlToJson;

import static com.hxs.xposedreddevil.ui.MainActivity.RED_FILE;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class RedHook {

    private XC_LoadPackage.LoadPackageParam classLoader;
    private static Activity launcherUiActivity = null;
    Gson gson = new Gson();
    MsgsBean bean = new MsgsBean();
    DBean dBean = new DBean();
    String nativeUrlString = "";
    String data = "";
    Map<String, Object> stringMap = new HashMap<>();

    public RedHook() {
    }

    public void init(XC_LoadPackage.LoadPackageParam classLoader) {
        if (this.classLoader == null) {
            this.classLoader = classLoader;
            hook(classLoader);
        }
    }

    public static RedHook getInstance() {
        return RedHookHolder.instance;
    }

    private static class RedHookHolder {
        @SuppressLint("StaticFieldLeak")
        private static final RedHook instance = new RedHook();
    }

    private void hook(final XC_LoadPackage.LoadPackageParam lpparam) {

        try {
            if (PropertiesUtils.getValue(RED_FILE, "redmain", "2").equals("2")) {
                return;
            }
            if (lpparam.packageName.equals("com.tencent.mm")) {
                log("监听微信");
                // hook微信插入数据的方法，监听红包消息
                XposedHelpers.findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase",
                        lpparam.classLoader, "insertWithOnConflict",
                        String.class, String.class, ContentValues.class, int.class, new XC_MethodHook() {
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                // 打印插入数据信息
                                log("------------------------insert start---------------------" + "\n\n");
                                log("param args1:" + param.args[0]);
                                log("param args1:" + param.args[1]);
                                ContentValues contentValues = (ContentValues) param.args[2];
                                for (Map.Entry<String, Object> item : contentValues.valueSet()) {
                                    if (item.getValue() != null) {
                                        log(item.getKey() + "---------" + item.getValue().toString());
                                        if (item.getKey().equals("xml")) {
                                            data = item.getValue().toString();
                                            log("红包外挂测试" + data);
                                        }
                                        stringMap.put(item.getKey(), item.getValue().toString());
                                    } else {
                                        log(item.getKey() + "---------" + "null");
                                        stringMap.put(item.getKey(), "null");
                                    }
                                }
                                log("------------------------insert over---------------------" + "\n\n");

                                // 判断插入的数据是否是发送过来的消息
                                String tableName = (String) param.args[0];
                                if (TextUtils.isEmpty(tableName) || !tableName.equals("message")) {
                                    return;
                                }
                                // 判断是否是红包消息类型
                                Integer type = contentValues.getAsInteger("type");
                                if (null == type) {
                                    return;
                                }
                                if (type == 436207665 || type == 469762097) {
                                    log("获取状态------------>" + PropertiesUtils.getValue(RED_FILE, "red", "2"));
                                    log("获取map------------>" + stringMap.get("isSend"));
                                    if (PropertiesUtils.getValue(RED_FILE, "red", "2").equals("1")) {
                                        if (!stringMap.get("isSend").equals(1)) {
                                            return;
                                        }

                                    }
//                                    Context context = (Context) callMethod(callStaticMethod(findClass("android.app.ActivityThread", null),
//                                            "currentActivityThread", new Object[0]), "getSystemContext", new Object[0]);
                                    if (PropertiesUtils.getValue(RED_FILE, "sound", "2").equals("1")) {
                                        PlaySoundUtils.Play();
                                    }
                                    if (PropertiesUtils.getValue(RED_FILE, "push", "2").equals("1")) {
                                        EventBus.getDefault().post(new MessageEvent("天降红包"));
                                    }
                                    if (PinYinUtils.getPingYin(PinYinUtils.parseXMLWithPull(data)).contains("gua") ||
                                            data.contains("圭") ||
                                            data.contains("G") ||
                                            data.contains("GUA") ||
                                            data.contains("gua") ||
                                            data.contains("g")) {
                                        return;
                                    }
                                    // 处理红包消息
                                    handleLuckyMoney(contentValues, lpparam);
                                }
                            }
                        });

                // hook 微信主界面的onCreate方法，获得主界面对象
                findAndHookMethod("com.tencent.mm.ui.LauncherUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        log("com.tencent.mm.ui.LauncherUI onCreated" + "\n");
                        launcherUiActivity = (Activity) param.thisObject;
                    }
                });

                // hook领取红包页面的onCreate方法，打印Intent中的参数（只起到调试作用）
                findAndHookMethod("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        String key_native_url = activity.getIntent().getStringExtra("key_native_url");
                        String key_username = activity.getIntent().getStringExtra("key_username");
                        int key_way = activity.getIntent().getIntExtra("key_way", 0);
                        log("key_native_url: " + key_native_url + "\n");
                        log("key_way: " + key_way + "\n");
                        log("key_username: " + key_username + "\n");
                    }
                });

                XposedHelpers.findAndHookMethod("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI", lpparam.classLoader, "c", int.class, int.class,
                        String.class, findClass("com.tencent.mm.ah.m", lpparam.classLoader), new XC_MethodHook() {
                            //进行hook操作
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                log("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI: Method d called" + "\n");
                                Field buttonField = XposedHelpers.findField(param.thisObject.getClass(), "mZE");
                                final Button kaiButton = (Button) buttonField.get(param.thisObject);
                                kaiButton.performClick();
                            }
                        });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 处理红包消息
    private void handleLuckyMoney(ContentValues contentValues, XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        // 获得发送人
        String talker = contentValues.getAsString("talker");

        // 从插入的数据库中获得nativeurl
        String content = contentValues.getAsString("content");
        if (!content.startsWith("<msg")) {
            content = content.substring(content.indexOf("<msg"));
        }

        XmlToJson wcpayinfo = new XmlToJson.Builder(content).build();
        try {
            bean = gson.fromJson(wcpayinfo.toFormattedString(""), MsgsBean.class);
            nativeUrlString = bean.getMsg().getAppmsg().getWcpayinfo().getNativeurl();
        } catch (JsonSyntaxException e) {
            dBean = gson.fromJson(wcpayinfo.toFormattedString(""), DBean.class);
            nativeUrlString = dBean.getMsg().getAppmsg().getWcpayinfo().getNativeurl();
        }
        log("nativeurl: " + nativeUrlString + "\n");
        if (PropertiesUtils.getValue(RED_FILE, "sleep", "2").equals("1")) {
            Thread.sleep(Long.parseLong(PropertiesUtils.getValue(RED_FILE, "sleeptime", "1")));
        }
        // 启动红包页面
        if (launcherUiActivity != null) {
            log("call method com.tencent.mm.br.d, start LuckyMoneyReceiveUI" + "\n");
            Intent paramau = new Intent();
            paramau.putExtra("key_way", 1);
            paramau.putExtra("key_native_url", nativeUrlString);
            paramau.putExtra("key_username", talker);
//            callStaticMethod(findClass("com.tencent.mm.br.d", lpparam.classLoader), "b", launcherUiActivity, "luckymoney", ".ui.LuckyMoneyReceiveUI", paramau);
            callStaticMethod(findClass("com.tencent.mm.br.d", lpparam.classLoader), "b",
                    launcherUiActivity, "luckymoney", ".ui.LuckyMoneyNotHookReceiveUI", paramau);
        } else {
            log("launcherUiActivity == null" + "\n");
        }
    }

}
