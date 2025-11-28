package com.minecraftlauncher.accounts;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * Account对象的JSON反序列化器
 * 负责将JSON数据转换为Account对象
 */
public class AccountDeserializer implements JsonDeserializer<Account> {

    @Override
    public Account deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        
        // 从JSON对象获取必要的字段
        String username = jsonObject.has("username") ? jsonObject.get("username").getAsString() : "";
        String typeStr = jsonObject.has("type") ? jsonObject.get("type").getAsString() : "OFFLINE";
        
        Account.AccountType type;
        try {
            type = Account.AccountType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = Account.AccountType.OFFLINE; // 默认使用离线账户类型
        }
        
        // 创建Account对象
        Account account = new Account(username, type);
        
        // 设置可选字段
        if (jsonObject.has("accessToken")) {
            account.setAccessToken(jsonObject.get("accessToken").getAsString());
        }
        
        if (jsonObject.has("refreshToken")) {
            account.setRefreshToken(jsonObject.get("refreshToken").getAsString());
        }
        
        if (jsonObject.has("uuid")) {
            account.setUuid(jsonObject.get("uuid").getAsString());
        }
        
        if (jsonObject.has("rememberMe")) {
            account.setRememberMe(jsonObject.get("rememberMe").getAsBoolean());
        }
        
        if (jsonObject.has("selected")) {
            account.setSelected(jsonObject.get("selected").getAsBoolean());
        }
        
        return account;
    }
}