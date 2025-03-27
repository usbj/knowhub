package com.rookie.admin.test;



import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;


@SpringBootTest
public class FrameworkTest {

    @Resource
    PasswordEncoder passwordEncoder;

    @Autowired
    UserInfoMapper userInfoMapper;

    @Value("${redis.base-key}")
    private String BASE_KEY;

    @Autowired
    RedisCache redisCache;

    @Value("${redis.expire-time}")
    private String EXPIRE_TIME;

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    TokenService tokenService;

    @Autowired
    SysUserService sysUserService;

    @Test
    void createAdminPassword(){
//        System.out.println(passwordEncoder.encode("ios507"));
        System.out.println(passwordEncoder.encode("rookie"));
    }

    @Test
    void testUserInfoMapper() {
        UserInfo admin = userInfoMapper.selectUserByUsername("admin");
        ArrayList<Integer> roles = userInfoMapper.selectRoleIdByUserId(admin.getUserId());
        ArrayList<Integer> menus = userInfoMapper.selectMenuIdByRoleId(roles);
        ArrayList<String> permKeyById = userInfoMapper.getPermKeyById(menus);
        System.out.println(permKeyById);
    }

    @Test
    void testLombok() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(1L);
        System.out.println(userInfo.getUserId());
    }

    @Test
    void testYmlValue(){
        System.out.println(BASE_KEY);
        System.out.println(EXPIRE_TIME);

    }

    @Test
    void testRedisCache() {
        String admin = JSONUtil.toJsonStr(userInfoMapper.selectUserByUsername("admin"));
        System.out.println(redisCache.setCacheToSetTime("admin", admin));
    }

    @Test
    void testDelete(){
        redisCache.deleteCache("admin");
    }

    @Test
    void testUserDetailService(){
        System.out.println(userDetailsService.loadUserByUsername("admin"));
    }

    @Test
    void testJwtUntil() {
//        String token = tokenService.createJwt("admin", "123456");
//        System.out.println(token);
//        System.out.println(tokenService.jwtVerification(token, "admin", "123456"));
//        System.out.println(tokenService.getUsernameByJwt(token));
    }

    @Test
    void testSysService() {
//        Result<PageInfo<SysUserVo>> listResult = sysUserService.quarrySysUser(null);
//        System.out.println(listResult);


//        UserQuarry userQuarry = new UserQuarry();
//        userQuarry.setUsername("admin");
//        Result<PageInfo<SysUserVo>> pageInfoResult = sysUserService.quarrySysUser(userQuarry);
//        SysUserVo sysUserVo = pageInfoResult.getData().getList().get(0);
//        SysUserVo userVo = sysUserService.selectSysUserVoById(sysUserVo.getUserId()).getData();
//        userVo.setPhoneNumber("22222222222");
//        sysUserService.editSysUserInfo(userVo);

//        SysUserVo userVo = new SysUserVo();
//        userVo.setUsername("rookie");
//        userVo.setSex("0");
//        userVo.setUsername("rookie");
//        userVo.setPhoneNumber("13866666666");
//        sysUserService.addSysUserInfo(userVo);

//        Long[] userId ={9L};
//        System.out.println(sysUserService.deleteSysUser(userId));

//        sysUserService.chargeSysUserStatus(2L,0);

        sysUserService.resetSysUserPassword(11L,"123456789");
    }


}
