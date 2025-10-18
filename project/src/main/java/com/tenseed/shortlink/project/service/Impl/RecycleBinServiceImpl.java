package com.tenseed.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tenseed.shortlink.project.dao.entity.ShortLinkDO;
import com.tenseed.shortlink.project.dao.mapper.ShortLinkMapper;
import com.tenseed.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.tenseed.shortlink.project.dto.req.RecycleBinRemoveReqDTO;
import com.tenseed.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.tenseed.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.tenseed.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.tenseed.shortlink.project.service.RecycleBinService;
import com.tenseed.shortlink.project.toolkit.LinkUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.tenseed.shortlink.project.common.constant.RedisKeyConstant.GOTO_NULL_SHORT_LINK_KEY;
import static com.tenseed.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

/**
 * 回收站管理接口实现层
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();
        baseMapper.update(shortLinkDO, updateWrapper);

        // 移动至回收站后需要删除 Redis 中对应缓存
        stringRedisTemplate.delete(
                String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl())
        );
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // 执行分页查询
        IPage<ShortLinkDO> resultPage = baseMapper.pageRecycleBinLink(requestParam);
        // 转换查询结果为响应 DTO，并添加 http 前缀
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + result.getDomain());  // 补全域名前缀
            return result;
        });
    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 1);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(0)
                .build();
        baseMapper.update(shortLinkDO, updateWrapper);

        // 移出回收站后需要删除 Redis 中对应的空缓存，防止跳转短链接时跳转到 404 页面
        stringRedisTemplate.delete(
                String.format(GOTO_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl())
        );

        // 重新构建查询条件获取恢复后的短链接信息
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO recoveredShortLinkDO = baseMapper.selectOne(queryWrapper);

        // 进行缓存预热：将短链接信息放入 Redis 缓存
        if (recoveredShortLinkDO != null) {
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, recoveredShortLinkDO.getFullShortUrl()),
                    recoveredShortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(recoveredShortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public void removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getEnableStatus, 1);

        updateWrapper.set(ShortLinkDO::getDelTime, System.currentTimeMillis())
                .set(ShortLinkDO::getDelFlag, 1);
        baseMapper.update(null, updateWrapper);
    }
}
