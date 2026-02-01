package com.quanxiaoha.ai.robot.service;

import com.quanxiaoha.ai.robot.model.dto.SearchResultDTO;

import java.util.List;

/**
 * @Description: SearXNG 搜索服务接口
 */
public interface SearXNGService {

    /**
     * 调用 SearXNG Api, 获取搜索列表
     * @param query 搜索关键词
     * @return
     */
    List<SearchResultDTO> search(String query);
}
