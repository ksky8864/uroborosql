/**
 * Copyright (c) 2017-present, Future Corporation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package jp.co.future.uroborosql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jp.co.future.uroborosql.context.SqlContext;
import jp.co.future.uroborosql.dialect.Dialect;
import jp.co.future.uroborosql.exception.EntitySqlRuntimeException;
import jp.co.future.uroborosql.exception.EntitySqlRuntimeException.EntityProcKind;
import jp.co.future.uroborosql.exception.UroborosqlRuntimeException;
import jp.co.future.uroborosql.fluent.SqlEntityQuery;
import jp.co.future.uroborosql.mapping.EntityHandler;
import jp.co.future.uroborosql.mapping.TableMetadata;
import jp.co.future.uroborosql.mapping.TableMetadata.Column;
import jp.co.future.uroborosql.utils.CaseFormat;

/**
 * SqlEntityQuery実装
 *
 * @param <E> Entity型
 * @author ota
 */
final class SqlEntityQueryImpl<E> extends AbstractExtractionCondition<SqlEntityQuery<E>> implements SqlEntityQuery<E> {
	private final EntityHandler<?> entityHandler;
	private final Class<? extends E> entityType;
	private final List<SortOrder> sortOrders;
	private long limit;
	private long offset;

	/**
	 * Constructor
	 *
	 * @param agent SqlAgent
	 * @param entityHandler EntityHandler
	 * @param tableMetadata TableMetadata
	 * @param context SqlContext
	 * @param entityType エンティティタイプ
	 */
	SqlEntityQueryImpl(final SqlAgent agent, final EntityHandler<?> entityHandler, final TableMetadata tableMetadata,
			final SqlContext context, final Class<? extends E> entityType) {
		super(agent, tableMetadata, context);
		this.entityHandler = entityHandler;
		this.entityType = entityType;
		this.sortOrders = new ArrayList<>();
		this.limit = -1;
		this.offset = -1;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#collect()
	 */
	@Override
	public List<E> collect() {
		try (Stream<E> stream = stream()) {
			return stream.collect(Collectors.toList());
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#first()
	 */
	@Override
	public Optional<E> first() {
		try (Stream<E> stream = stream()) {
			return stream.findFirst();
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#stream()
	 */
	@Override
	public Stream<E> stream() {
		try {
			StringBuilder sql = new StringBuilder(context().getSql()).append(getWhereClause())
					.append(getOrderByClause());
			Dialect dialect = agent().getSqlConfig().getDialect();
			if (dialect.supportsLimitClause()) {
				sql.append(dialect.getLimitClause(this.limit, this.offset));
			}

			context().setSql(sql.toString());
			return this.entityHandler.doSelect(agent(), context(), this.entityType);
		} catch (final SQLException e) {
			throw new EntitySqlRuntimeException(EntityProcKind.SELECT, e);
		}

	}

	/**
	 * ORDER BY句を生成する
	 *
	 * @return ORDER BY句の文字列
	 */
	@SuppressWarnings("unchecked")
	private String getOrderByClause() {
		boolean firstFlag = true;
		List<TableMetadata.Column> keys;
		Map<TableMetadata.Column, SortOrder> existsSortOrders = new HashMap<>();

		if (this.sortOrders.isEmpty()) {
			// ソート条件の指定がない場合は主キーでソートする
			keys = (List<TableMetadata.Column>) this.tableMetadata.getKeyColumns();
			for (Column key : keys) {
				existsSortOrders.put(key, new SortOrder(key.getCamelColumnName(), Order.ASCENDING));
			}
		} else {
			// ソート条件の指定がある場合は指定されたカラムでソートする
			keys = new ArrayList<>();
			for (SortOrder sortOrder : sortOrders) {
				String snakeCol = CaseFormat.UPPER_SNAKE_CASE.convert(sortOrder.getCol());
				for (TableMetadata.Column metaCol : this.tableMetadata.getColumns()) {
					if (snakeCol.equalsIgnoreCase(metaCol.getColumnName())) {
						keys.add(metaCol);
						existsSortOrders.put(metaCol, sortOrder);
						break;
					}
				}
			}
		}

		if (!keys.isEmpty()) {
			StringBuilder sql = new StringBuilder();
			Dialect dialect = agent().getSqlConfig().getDialect();
			sql.append("ORDER BY").append(System.lineSeparator());
			firstFlag = true;
			for (final TableMetadata.Column key : keys) {
				SortOrder sortOrder = existsSortOrders.get(key);
				sql.append("\t");
				if (firstFlag) {
					sql.append("  ");
					firstFlag = false;
				} else {
					sql.append(", ");
				}
				sql.append(key.getColumnIdentifier()).append(" ").append(sortOrder.getOrder().toString());
				if (dialect.supportsNullValuesOrdering()) {
					sql.append(" ").append(sortOrder.getNulls().toString());
				}
				sql.append(System.lineSeparator());
			}
			return sql.toString();
		} else {
			return "";
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#asc(java.lang.String[])
	 */
	@Override
	public SqlEntityQuery<E> asc(final String... cols) {
		for (String col : cols) {
			this.sortOrders.add(new SortOrder(col, Order.ASCENDING));
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#asc(java.lang.String, jp.co.future.uroborosql.fluent.SqlEntityQuery.Nulls)
	 */
	@Override
	public SqlEntityQuery<E> asc(final String col, final Nulls nulls) {
		this.sortOrders.add(new SortOrder(col, Order.ASCENDING, nulls));
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#desc(java.lang.String[])
	 */
	@Override
	public SqlEntityQuery<E> desc(final String... cols) {
		for (String col : cols) {
			this.sortOrders.add(new SortOrder(col, Order.DESCENDING));
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	s	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#desc(java.lang.String, jp.co.future.uroborosql.fluent.SqlEntityQuery.Nulls)
	 */
	@Override
	public SqlEntityQuery<E> desc(final String col, final Nulls nulls) {
		this.sortOrders.add(new SortOrder(col, Order.DESCENDING, nulls));
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#limit(long)
	 */
	@Override
	public SqlEntityQuery<E> limit(final long limit) {
		if (!agent().getSqlConfig().getDialect().supportsLimitClause()) {
			throw new UroborosqlRuntimeException("Unsupported limit clause.");
		}
		this.limit = limit;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see jp.co.future.uroborosql.fluent.SqlEntityQuery#offset(long)
	 */
	@Override
	public SqlEntityQuery<E> offset(final long offset) {
		if (!agent().getSqlConfig().getDialect().supportsLimitClause()) {
			throw new UroborosqlRuntimeException("Unsupported offset clause.");
		}
		this.offset = offset;
		return this;
	}

	/**
	 * Sort Order
	 */
	private static class SortOrder {
		private final String col;
		private final Order order;
		private final Nulls nulls;

		/**
		 * Constructor
		 *
		 * @param col sort column name (camelCase)
		 * @param order {@link Order}
		 */
		SortOrder(final String col, final Order order) {
			this(col, order, Nulls.LAST);
		}

		/**
		 * Constructor
		 *
		 * @param col sort column name (camelCase)
		 * @param order {@link Order}
		 * @param nulls {@link Nulls}
		 */
		SortOrder(final String col, final Order order, final Nulls nulls) {
			if (col == null) {
				throw new UroborosqlRuntimeException("argment col is required.");
			}
			this.col = col;
			this.order = order != null ? order : Order.ASCENDING;
			this.nulls = nulls != null ? nulls : Nulls.LAST;
		}

		final String getCol() {
			return col;
		}

		final Order getOrder() {
			return order;
		}

		final Nulls getNulls() {
			return nulls;
		}
	}
}
