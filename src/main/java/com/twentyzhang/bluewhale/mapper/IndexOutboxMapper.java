package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IndexOutboxMapper extends BaseMapper<IndexOutbox> {

    /** 按 id 顺序拉取一批 PENDING 事件。 */
    @Select("SELECT * FROM index_outbox WHERE status = 'PENDING' ORDER BY id LIMIT #{limit}")
    List<IndexOutbox> selectPending(@Param("limit") int limit);

    /** 标记处理成功。 */
    @Update("UPDATE index_outbox SET status = 'DONE' WHERE id = #{id}")
    int markDone(@Param("id") Long id);

    /** 处理失败但仍可重试：累加重试次数、记录错误，保持 PENDING。 */
    @Update("UPDATE index_outbox SET retry_count = retry_count + 1, last_error = #{err} WHERE id = #{id}")
    int markRetry(@Param("id") Long id, @Param("err") String err);

    /** 重试超限：置 FAILED。 */
    @Update("UPDATE index_outbox SET status = 'FAILED', retry_count = retry_count + 1, last_error = #{err} WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("err") String err);
}
