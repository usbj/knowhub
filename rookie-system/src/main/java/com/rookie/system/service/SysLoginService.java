package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.LoginBody;




public interface SysLoginService {

    String loginVerification(LoginBody loginBody);
}
