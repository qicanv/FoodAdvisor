package com.foodadvisor.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 字段与 Java String 之间的转换器。
 *
 * Java 中用 String 保存 JSON 文本，
 * 写入数据库时按 PostgreSQL OTHER/JSONB 类型传递，
 * 读取数据库时再转换回 String。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    /**
     * 将 Java String 写入 PostgreSQL JSONB 字段。
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int index,
            String parameter,
            JdbcType jdbcType
    ) throws SQLException {
        ps.setObject(index, parameter, Types.OTHER);
    }

    /**
     * 根据字段名读取 JSONB。
     */
    @Override
    public String getNullableResult(
            ResultSet rs,
            String columnName
    ) throws SQLException {
        return rs.getString(columnName);
    }

    /**
     * 根据字段序号读取 JSONB。
     */
    @Override
    public String getNullableResult(
            ResultSet rs,
            int columnIndex
    ) throws SQLException {
        return rs.getString(columnIndex);
    }

    /**
     * 从存储过程结果中读取 JSONB。
     */
    @Override
    public String getNullableResult(
            CallableStatement cs,
            int columnIndex
    ) throws SQLException {
        return cs.getString(columnIndex);
    }
}