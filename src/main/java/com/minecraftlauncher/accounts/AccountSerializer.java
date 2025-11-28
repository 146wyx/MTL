package com.minecraftlauncher.accounts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Account对象的JSON序列化器
 * 负责将Account对象转换为JSON格式
 */
public class AccountSerializer implements JsonSerializer<Account> {

    @Override
    public JsonElement serialize(Account account, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        // 添加必要的字段到JSON对象
        jsonObject.addProperty("username", account.getUsername());
        jsonObject.addProperty("type", account.getType().toString());
        jsonObject.addProperty("accessToken", account.getAccessToken());
        jsonObject.addProperty("refreshToken", account.getRefreshToken());
        jsonObject.addProperty("uuid", account.getUuid());
        jsonObject.addProperty("rememberMe", account.isRememberMe());
        jsonObject.addProperty("selected", account.isSelected());
        
        return jsonObject;
    }
}