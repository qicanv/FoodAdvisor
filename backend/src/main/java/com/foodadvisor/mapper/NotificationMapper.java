package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT * FROM notifications WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Notification> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM notifications WHERE user_id = #{userId} AND status = 'UNREAD' ORDER BY created_at DESC")
    List<Notification> selectUnreadByUserId(@Param("userId") Long userId);

    @Update("UPDATE notifications SET status = 'READ' WHERE id = #{id}")
    int markAsRead(@Param("id") Long id);

    @Update("UPDATE notifications SET status = 'READ' WHERE user_id = #{userId}")
    int markAllAsRead(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND status = 'UNREAD'")
    Long countUnread(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND review_id = #{reviewId}")
    Long countByReviewId(@Param("userId") Long userId, @Param("reviewId") Long reviewId);
}