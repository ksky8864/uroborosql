package jp.co.future.uroborosql.context;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import jp.co.future.uroborosql.exception.UroborosqlSQLException;
import jp.co.future.uroborosql.utils.CaseFormat;
import jp.co.future.uroborosql.utils.StringUtils;

public class SqlContextFactoryAutoParameterBinderTest {
	private static SqlConfig config;

	@BeforeAll
	public static void setUpClass() throws Exception {
		config = UroboroSQL.builder("jdbc:h2:mem:SqlContextFactoryUpdateAutoParameterBinderTest;DB_CLOSE_DELAY=-1",
				"sa",
				null).build();

		try (var agent = config.agent()) {
			var sqls = new String(Files.readAllBytes(Paths.get("src/test/resources/sql/ddl/create_tables.sql")),
					StandardCharsets.UTF_8).split(";");
			for (String sql : sqls) {
				if (StringUtils.isNotBlank(sql)) {
					agent.updateWith(sql.trim()).count();
				}
			}
			agent.commit();
		} catch (UroborosqlSQLException ex) {
			ex.printStackTrace();
			assertThat(ex.getMessage(), false);
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		try (var agent = config.agent()) {
			var dt = LocalDateTime.of(2017, 1, 1, 0, 0, 0);

			agent.updateWith("truncate table PRODUCT").count();
			agent.update("example/insert_product")
					.param("product_id", 1)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("ins_datetime", dt)
					.param("upd_datetime", dt)
					.param("version_no", 1)
					.count();
			agent.commit();
		}
	}

	@Test
	public void testSingleQueryAutoParameterBinder() {
		final var insDate = LocalDateTime.of(2016, 12, 31, 0, 0, 0, 0);
		final var updDate = LocalDateTime.of(2017, 1, 2, 12, 23, 30, 0);
		Consumer<SqlContext> binder = ctx -> ctx.param("upd_datetime", insDate);
		config.getSqlContextFactory().addQueryAutoParameterBinder(binder);

		try (var agent = config.agent()) {
			var productId = 10;
			// insert
			agent.update("example/insert_product")
					.param("product_id", productId)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("ins_datetime", insDate)
					.param("upd_datetime", updDate)
					.param("version_no", 1)
					.count();

			var row = agent.query("example/select_product").param("product_id", productId).first();
			assertThat(row.get("INS_DATETIME"), is(Timestamp.valueOf(insDate)));
			// QueryAutoParameterBinderはupdateでは適用されないためupdDateとなる
			assertThat(row.get("UPD_DATETIME"), is(Timestamp.valueOf(updDate)));

			config.getSqlContextFactory().removeQueryAutoParameterBinder(binder);

			binder = ctx -> ctx.param("upd_datetime", updDate);
			config.getSqlContextFactory().addQueryAutoParameterBinder(binder);

			assertThat(agent.query("example/select_product_where_upd_datetime").collect().size(), is(1));

			config.getSqlContextFactory().removeQueryAutoParameterBinder(binder);
		}

	}

	@Test
	public void testSingleUpdateAutoParameterBinder() {
		final var insDate = LocalDateTime.of(2016, 12, 31, 0, 0, 0, 0);
		final var updDate = LocalDateTime.of(2017, 1, 2, 12, 23, 30, 0);
		Consumer<SqlContext> binder = ctx -> ctx.param("upd_datetime", updDate);
		config.getSqlContextFactory().addUpdateAutoParameterBinder(binder);

		try (var agent = config.agent()) {
			var productId = 10;
			// insert
			agent.update("example/insert_product")
					.param("product_id", productId)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("ins_datetime", insDate)
					.param("upd_datetime", insDate)
					.param("version_no", 1)
					.count();

			var row = agent.query("example/select_product").param("product_id", productId).first();
			assertThat(row.get("INS_DATETIME"), is(Timestamp.valueOf(insDate)));
			// UpdateAutoParameterBinderのほうが後で設定されるため、上書きされる）
			assertThat(row.get("UPD_DATETIME"), is(Timestamp.valueOf(updDate)));

			// UpdateAutoParameterBinderはQueryでは適用されない
			assertThat(
					agent.queryWith(
							"select * from PRODUCT where 1 = 1/*IF upd_datetime != null */ AND UPD_DATETIME = /*upd_datetime*/ /*END*/")
							.collect().size(),
					is(2));
		}

		config.getSqlContextFactory().removeUpdateAutoParameterBinder(binder);
	}

	@Test
	public void testMultiQueryAutoParameterBinder() {
		final var colVarchar = "varchar";
		final var colNumeric = new BigDecimal("10.00");
		final var colTimestamp = LocalDateTime.now();

		var factory = config.getSqlContextFactory();

		Consumer<SqlContext> binder1 = ctx -> ctx.param("col_varchar", colVarchar);
		factory.addQueryAutoParameterBinder(binder1);
		Consumer<SqlContext> binder2 = ctx -> ctx.param("col_numeric", colNumeric);
		factory.addQueryAutoParameterBinder(binder2);
		Consumer<SqlContext> binder3 = ctx -> ctx.param("col_timestamp", colTimestamp);
		factory.addQueryAutoParameterBinder(binder3);

		try (var agent = config.agent()) {
			// insert match
			agent.update("example/insert_column_type_test")
					.param("col_varchar", colVarchar)
					.param("col_char", "A")
					.param("col_numeric", colNumeric)
					.param("col_boolean", true)
					.param("col_timestamp", colTimestamp)
					.param("col_date", LocalDate.now())
					.param("col_time", LocalTime.now())
					.count();
			// insert unmatch
			agent.update("example/insert_column_type_test")
					.param("col_varchar", colVarchar + "unmatch")
					.param("col_char", "A")
					.param("col_numeric", colNumeric.add(BigDecimal.ONE))
					.param("col_boolean", true)
					.param("col_timestamp", colTimestamp.minusDays(1))
					.param("col_date", LocalDate.now())
					.param("col_time", LocalTime.now())
					.count();
			agent.update("example/insert_column_type_test")
					.param("col_varchar", colVarchar + "unmatch")
					.param("col_char", "A")
					.param("col_numeric", colNumeric)
					.param("col_boolean", true)
					.param("col_timestamp", colTimestamp)
					.param("col_date", LocalDate.now())
					.param("col_time", LocalTime.now())
					.count();
			agent.update("example/insert_column_type_test")
					.param("col_varchar", colVarchar)
					.param("col_char", "A")
					.param("col_numeric", colNumeric.add(BigDecimal.ONE))
					.param("col_boolean", true)
					.param("col_timestamp", colTimestamp)
					.param("col_date", LocalDate.now())
					.param("col_time", LocalTime.now())
					.count();
			agent.update("example/insert_column_type_test")
					.param("col_varchar", colVarchar)
					.param("col_char", "A")
					.param("col_numeric", colNumeric)
					.param("col_boolean", true)
					.param("col_timestamp", colTimestamp.plusDays(1))
					.param("col_date", LocalDate.now())
					.param("col_time", LocalTime.now())
					.count();
			agent.commit();

			var data = agent.query("example/select_column_type_test").collect(
					CaseFormat.LOWER_SNAKE_CASE);
			assertThat(data.size(), is(1));
			var row = data.get(0);
			assertThat(row.get("col_varchar"), is(colVarchar));
			assertThat(row.get("col_numeric"), is(colNumeric));
			assertThat(row.get("col_timestamp"), is(Timestamp.valueOf(colTimestamp)));
		}

		factory.removeQueryAutoParameterBinder(binder1);
		factory.removeQueryAutoParameterBinder(binder2);
		factory.removeQueryAutoParameterBinder(binder3);
	}

	@Test
	public void testMultiUpdateAutoParameterBinder() {
		final var insDate = LocalDateTime.of(2016, 12, 31, 0, 0, 0, 0);
		final var updDate = LocalDateTime.of(2017, 1, 2, 12, 23, 30, 0);

		var factory = config.getSqlContextFactory();

		Consumer<SqlContext> binder1 = ctx -> ctx.param("ins_datetime", insDate);
		factory.addUpdateAutoParameterBinder(binder1);
		Consumer<SqlContext> binder2 = ctx -> ctx.param("upd_datetime", updDate);
		factory.addUpdateAutoParameterBinder(binder2);
		Consumer<SqlContext> binder3 = ctx -> ctx.param("upd_datetime", ((LocalDateTime) ctx.getParam("upd_datetime")
				.getValue()).plusDays(1));
		factory.addUpdateAutoParameterBinder(binder3);

		try (var agent = config.agent()) {
			var productId = 10;
			// insert
			agent.update("example/insert_product")
					.param("product_id", productId)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("version_no", 1)
					.count();

			var row = agent.query("example/select_product").param("product_id", productId).first();
			assertThat(row.get("INS_DATETIME"), is(Timestamp.valueOf(insDate)));
			// UpdateAutoParameterBinderのほうが後で設定されるため、上書きされる）
			assertThat(row.get("UPD_DATETIME"), is(Timestamp.valueOf(updDate.plusDays(1))));
		}

		factory.removeUpdateAutoParameterBinder(binder1);
		factory.removeUpdateAutoParameterBinder(binder2);
		factory.removeUpdateAutoParameterBinder(binder3);
	}

	@Test
	public void testMultiUpdateAutoParameterBinderIfAbsent() {
		final var insDate = LocalDateTime.of(2016, 12, 31, 0, 0, 0, 0);
		final var updDate = LocalDateTime.of(2017, 1, 2, 12, 23, 30, 0);

		var factory = config.getSqlContextFactory();

		Consumer<SqlContext> binder1 = ctx -> ctx.param("ins_datetime", insDate);
		factory.addUpdateAutoParameterBinder(binder1);
		Consumer<SqlContext> binder2 = ctx -> ctx.param("upd_datetime", updDate);
		factory.addUpdateAutoParameterBinder(binder2);
		Consumer<SqlContext> binder3 = ctx -> ctx.paramIfAbsent("upd_datetime", updDate.plusDays(1));
		factory.addUpdateAutoParameterBinder(binder3);

		try (var agent = config.agent()) {
			var productId = 10;
			// insert
			agent.update("example/insert_product")
					.param("product_id", productId)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("version_no", 1)
					.param("ins_datetime", LocalDateTime.of(2016, 1, 1, 0, 0, 0, 0))
					.param("upd_datetime", LocalDateTime.of(2016, 1, 1, 0, 0, 0, 0))
					.count();

			var row = agent.query("example/select_product").param("product_id", productId).first();
			assertThat(row.get("INS_DATETIME"), is(Timestamp.valueOf(insDate)));
			// UpdateAutoParameterBinderのほうが後で設定されるため、上書きされる）
			assertThat(row.get("UPD_DATETIME"), is(Timestamp.valueOf(updDate)));
		}

		factory.removeUpdateAutoParameterBinder(binder1);
		factory.removeUpdateAutoParameterBinder(binder2);
		factory.removeUpdateAutoParameterBinder(binder3);
	}

	@Test
	public void testQueryAutoBindIfCase() {
		var factory = config.getSqlContextFactory();

		final var productId = 2;
		Consumer<SqlContext> binder1 = ctx -> ctx.paramIfAbsent("product_id", productId);
		factory.addQueryAutoParameterBinder(binder1);

		try (var agent = config.agent()) {
			// insert
			assertThat(agent.update("example/insert_product")
					.param("product_id", productId)
					.param("product_name", "name")
					.param("product_kana_name", "kana")
					.param("jan_code", "1234567890123")
					.param("product_description", "description")
					.param("version_no", 1)
					.param("ins_datetime", LocalDateTime.of(2016, 1, 1, 0, 0, 0, 0))
					.param("upd_datetime", LocalDateTime.of(2016, 1, 1, 0, 0, 0, 0))
					.count(), is(1));

			// query
			assertThat(agent.query("example/select_product").collect().size(), is(1));

			factory.removeQueryAutoParameterBinder(binder1);
			// query
			assertThat(agent.query("example/select_product").collect().size(), is(2));
		}
	}

	@Test
	public void testBatchUpdateAutoBind() {
		var factory = config.getSqlContextFactory();

		Consumer<SqlContext> binder1 = ctx -> ctx.param("ins_datetime", LocalDateTime.now());
		factory.addUpdateAutoParameterBinder(binder1);

		var count = 1000;
		try (var agent = config.agent()) {
			agent.updateWith("truncate table PRODUCT").count();

			List<Map<String, Object>> input = new ArrayList<>();
			for (var i = 1; i <= count; i++) {
				Map<String, Object> map = new HashMap<>();
				map.put("product_id", i * 10);
				map.put("product_name", "name");
				map.put("product_kana_name", "kana");
				map.put("jan_code", "1234567890123");
				map.put("product_description", "description");
				map.put("version_no", 1);
				map.put("upd_datetime", null);
				input.add(map);
			}

			// insert
			assertThat(agent.batch("example/insert_product").paramStream(input.stream()).count(), is(count));

			var dateCount = agent.query("example/select_product").stream(CaseFormat.LOWER_SNAKE_CASE)
					.map(r -> r.get("ins_datetime"))
					.distinct().count();
			// ins_datetimeが同じ時間になっていないことを確認
			assertThat(dateCount, not(1));
		}

		factory.removeUpdateAutoParameterBinder(binder1);
	}

}
