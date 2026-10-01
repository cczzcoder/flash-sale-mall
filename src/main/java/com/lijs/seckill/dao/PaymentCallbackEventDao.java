package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.PaymentCallbackEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentCallbackEventDao extends BaseMapper<PaymentCallbackEvent> {

    @Insert("insert into payment_callback_event " +
            "(order_id,payment_no,provider,transaction_id,process_result,create_date) " +
            "values (#{orderId},#{paymentNo},#{provider},#{transactionId},#{processResult},#{createDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertEvent(PaymentCallbackEvent event);

    @Update("update payment_callback_event set process_result=#{processResult} where id=#{id}")
    int updateResult(@Param("id") long id, @Param("processResult") String processResult);

    @Select("select * from payment_callback_event where provider=#{provider} and transaction_id=#{transactionId}")
    PaymentCallbackEvent findByTransaction(@Param("provider") String provider,
                                           @Param("transactionId") String transactionId);
}
