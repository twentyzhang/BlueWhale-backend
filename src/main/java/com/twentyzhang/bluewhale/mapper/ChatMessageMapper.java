package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
