package com.knowhub.service.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.enums.ViewBizType;
import com.knowhub.mapper.UserViewHistoryMapper;
import com.knowhub.pojo.entity.UserViewHistory;
import com.knowhub.pojo.quarry.ViewHistoryQuarry;
import com.knowhub.pojo.vo.ViewHistoryVo;
import com.knowhub.service.ViewHistoryService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 统一浏览历史 Service 实现。
 * <p>
 * 防刷计数：recordView 事务内查 (user_id,biz_type,biz_id) 命中行——命中只累加 viewCount（主表不动）；
 * 未命中 INSERT 一行并对主表 view_count +1（独立访客，刷不动）。
 * 未登录态不调 recordView（由各详情 ServiceImpl 在确认登录后调，见第二条链路决策#3）。
 */
@Service
public class ViewHistoryServiceImpl implements ViewHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ViewHistoryServiceImpl.class);

    @Autowired
    UserViewHistoryMapper userViewHistoryMapper;

    @Override
    @Transactional
    public void recordView(Long userId, String bizType, Long bizId) {
        if (userId == null || bizType == null || bizId == null) {
            // 未登录或参数缺失不计浏览量（与决策#3"未登录不计浏览量"一致）
            return;
        }
        // 校验 bizType 是合法枚举值（防注入 + 防脏值，incrementMainViewCount 用它拼表名）
        if (!isValidBizType(bizType)) {
            log.warn("recordView 收到非法 bizType={}, bizId={}, 忽略", bizType, bizId);
            return;
        }
        UserViewHistory exist = userViewHistoryMapper.getByUserBiz(userId, bizType, bizId);
        if (exist != null) {
            // 命中：只累加 viewCount + 刷新 last_view_time，主表 view_count 不动（刷不动）
            userViewHistoryMapper.incrementViewCount(exist.getViewId());
            return;
        }
        // 未命中：INSERT 一行（首次访问该内容），并对主表 view_count +1 记独立访客
        UserViewHistory record = new UserViewHistory();
        record.setUserId(userId);
        record.setBizType(bizType);
        record.setBizId(bizId);
        userViewHistoryMapper.insertViewHistory(record);
        userViewHistoryMapper.incrementMainViewCount(bizType, bizId);
    }

    @Override
    public PageInfo<ViewHistoryVo> listHistory(String bizType) {
        Long userId = currentUser().getUserId();
        ViewHistoryQuarry quarry = new ViewHistoryQuarry();
        quarry.setUserId(userId);
        quarry.setBizType(bizType);
        PageUtil.startPage();
        List<ViewHistoryVo> list = userViewHistoryMapper.listViewHistory(quarry);
        return new PageInfo<>(list);
    }

    @Override
    public Boolean deleteHistory(Long viewId) {
        if (viewId == null) {
            throw new ServiceException(500, "参数缺失");
        }
        Long userId = currentUser().getUserId();
        return userViewHistoryMapper.deleteViewById(viewId, userId);
    }

    @Override
    public Boolean clearHistory() {
        Long userId = currentUser().getUserId();
        return userViewHistoryMapper.clearByUser(userId);
    }

    /** 校验 bizType 为合法枚举值（防 incrementMainViewCount 拼 ${} 表名时的注入） */
    private boolean isValidBizType(String bizType) {
        for (ViewBizType t : ViewBizType.values()) {
            if (t.getCode().equals(bizType)) {
                return true;
            }
        }
        return false;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}