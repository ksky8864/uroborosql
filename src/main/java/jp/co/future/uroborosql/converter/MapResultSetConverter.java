/**
 * Copyright (c) 2017-present, Future Corporation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package jp.co.future.uroborosql.converter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import jp.co.future.uroborosql.config.SqlConfig;
import jp.co.future.uroborosql.mapping.mapper.PropertyMapperManager;
import jp.co.future.uroborosql.utils.CaseFormat;

/**
 * 検索結果の1行をMap型に変換する変換器
 *
 * @author H.Sugimoto
 *
 */
public class MapResultSetConverter implements ResultSetConverter<Map<String, Object>> {
	private final PropertyMapperManager mapperManager;
	private final SqlConfig sqlConfig;
	private final CaseFormat caseFormat;

	/**
	 * コンストラクタ
	 *
	 * @param sqlConfig SQL設定クラス
	 */
	public MapResultSetConverter(final SqlConfig sqlConfig) {
		this(sqlConfig, CaseFormat.UPPER_SNAKE_CASE);
	}

	/**
	 * コンストラクタ
	 *
	 * @param sqlConfig SQL設定クラス
	 * @param caseFormat 変換するMapのキーの書式
	 */
	public MapResultSetConverter(final SqlConfig sqlConfig, final CaseFormat caseFormat) {
		this.sqlConfig = sqlConfig;
		this.caseFormat = caseFormat;
		this.mapperManager = new PropertyMapperManager(sqlConfig.getClock());
	}

	/**
	 * コンストラクタ
	 *
	 * @param sqlConfig SQL設定クラス
	 * @param caseFormat 変換するMapのキーの書式
	 * @param mapperManager コピー元のパラメータ変換クラス
	 */
	public MapResultSetConverter(final SqlConfig sqlConfig, final CaseFormat caseFormat,
			final PropertyMapperManager mapperManager) {
		this.sqlConfig = sqlConfig;
		this.caseFormat = caseFormat;
		this.mapperManager = new PropertyMapperManager(mapperManager, sqlConfig.getClock());
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.converter.ResultSetConverter#createRecord(java.sql.ResultSet)
	 */
	@Override
	public Map<String, Object> createRecord(final ResultSet rs) throws SQLException {
		var rsmd = rs.getMetaData();
		var columnCount = rsmd.getColumnCount();
		Map<String, Object> record = new LinkedHashMap<>(columnCount);
		for (var i = 1; i <= columnCount; i++) {
			record.put(caseFormat.convert(rsmd.getColumnLabel(i)), getValue(rs, rsmd, i));
		}
		return record;
	}

	/**
	 * ResultSetからMapperManager経由で値を取得する
	 *
	 * @param rs ResultSet
	 * @param rsmd ResultSetMetadata
	 * @param columnIndex カラムインデックス
	 * @return 指定したカラムインデックスの値
	 * @throws SQLException
	 */
	private Object getValue(final ResultSet rs, final ResultSetMetaData rsmd, final int columnIndex)
			throws SQLException {
		var javaType = this.sqlConfig.getDialect().getJavaType(rsmd.getColumnType(columnIndex),
				rsmd.getColumnTypeName(columnIndex));
		return this.mapperManager.getValue(javaType, rs, columnIndex);
	}

}
