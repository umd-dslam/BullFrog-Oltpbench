/******************************************************************************
 *  Copyright 2015 by OLTPBenchmark Project                                   *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 ******************************************************************************/

package com.oltpbenchmark.benchmarks.tpcc.procedures;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

import org.apache.log4j.Logger;

import com.oltpbenchmark.DBWorkload;

import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.tpcc.TPCCConstants;
import com.oltpbenchmark.benchmarks.tpcc.TPCCUtil;
import com.oltpbenchmark.benchmarks.tpcc.TPCCWorker;

public class StockLevelLazyMigrationSplit extends TPCCProcedure {

    private static final Logger LOG = Logger.getLogger(StockLevelLazyMigrationSplit.class);

	public SQLStmt stockGetDistOrderIdSQL = new SQLStmt(
	        "SELECT D_NEXT_O_ID " + 
            "  FROM " + TPCCConstants.TABLENAME_DISTRICT +
	        " WHERE D_W_ID = ? " +
            "   AND D_ID = ?");

	public final SQLStmt migrateOrderLineSQL1 = new SQLStmt(
			"migrate 1 orderline_stock " +
			"explain select count(*) from os_orderline_split_v " +
			"where ol_w_id = ?" +
			"  and ol_d_id = ?" +
			"  and ol_o_id < ?" +
			"  and ol_o_id >= ?" + 
			"  and s_w_id = ?" +
			"  and s_quantity < ?");
				
	public String migrateOrderLineSQL2 =
			"migrate insert into order_line( " +
			"  ol_w_id, ol_d_id, ol_o_id, ol_number, ol_i_id, ol_delivery_d, " +
			"  ol_amount, ol_supply_w_id, ol_quantity, ol_dist_info) " +
			" (select " +
			"  ol_w_id, ol_d_id, ol_o_id, ol_number, ol_i_id, ol_delivery_d, " +
			"  ol_amount, ol_supply_w_id, ol_quantity, ol_dist_info " +
			"  from orderline_stock) ";

	public final SQLStmt migrateStackSQL1 = new SQLStmt(
			"migrate 1 orderline_stock " +
			"explain select count(*) from os_stock_split_v " +
			"where ol_w_id = ?" +
			"  and ol_d_id = ?" +
			"  and ol_o_id < ?" +
			"  and ol_o_id >= ?" + 
			"  and s_w_id = ?" +
			"  and s_quantity < ?");

	public String migrateStackSQL2 =
			"migrate insert into stock(" +
			" s_w_id, s_i_id, s_quantity, s_ytd, s_order_cnt, s_remote_cnt, s_data, " +
			"  s_dist_01, s_dist_02, s_dist_03, s_dist_04, s_dist_05, s_dist_06, " +
			"  s_dist_07, s_dist_08, s_dist_09, s_dist_10) " +
			" (select " +
			"  s_w_id, s_i_id, s_quantity, s_ytd, s_order_cnt, s_remote_cnt, s_data, " +
			"  s_dist_01, s_dist_02, s_dist_03, s_dist_04, s_dist_05, s_dist_06, " +
			"  s_dist_07, s_dist_08, s_dist_09, s_dist_10 " +
			"  from orderline_stock) ";

	public SQLStmt stockGetCountStockSQL = new SQLStmt(
	        "SELECT COUNT(DISTINCT (S_I_ID)) AS STOCK_COUNT " +
			" FROM " + TPCCConstants.TABLENAME_ORDERLINE + ", " + TPCCConstants.TABLENAME_STOCK +
			" WHERE OL_W_ID = ?" +
			" AND OL_D_ID = ?" +
			" AND OL_O_ID < ?" +
			" AND OL_O_ID >= ?" +
			" AND S_W_ID = ?" +
			" AND S_I_ID = OL_I_ID" + 
			" AND S_QUANTITY < ?");

	// Stock Level Txn
	private PreparedStatement stockGetDistOrderId = null;
	private PreparedStatement stockGetCountStock = null;
    private PreparedStatement migrateOrderLine1 = null;
	// private PreparedStatement migrateOrderLine2 = null;
	private PreparedStatement migrateStack1 = null;
	// private PreparedStatement migrateStack2 = null;
	private Statement stmt = null;

	public ResultSet run(Connection conn, Random gen,
			int w_id, int numWarehouses,
			int terminalDistrictLowerID, int terminalDistrictUpperID,
			TPCCWorker w) throws SQLException {

        if (DBWorkload.IS_CONFLICT) {
			migrateOrderLineSQL2 =
			"migrate insert into order_line( " +
			"  ol_w_id, ol_d_id, ol_o_id, ol_number, ol_i_id, ol_delivery_d, " +
			"  ol_amount, ol_supply_w_id, ol_quantity, ol_dist_info) " +
			" (select " +
			"  ol_w_id, ol_d_id, ol_o_id, ol_number, ol_i_id, ol_delivery_d, " +
			"  ol_amount, ol_supply_w_id, ol_quantity, ol_dist_info " +
			"  from orderline_stock) " +
			"on conflict (ol_w_id,ol_d_id,ol_o_id,ol_number) do nothing";

			migrateStackSQL2 =
			"migrate insert into stock(" +
			" s_w_id, s_i_id, s_quantity, s_ytd, s_order_cnt, s_remote_cnt, s_data, " +
			"  s_dist_01, s_dist_02, s_dist_03, s_dist_04, s_dist_05, s_dist_06, " +
			"  s_dist_07, s_dist_08, s_dist_09, s_dist_10) " +
			" (select " +
			"  s_w_id, s_i_id, s_quantity, s_ytd, s_order_cnt, s_remote_cnt, s_data, " +
			"  s_dist_01, s_dist_02, s_dist_03, s_dist_04, s_dist_05, s_dist_06, " +
			"  s_dist_07, s_dist_08, s_dist_09, s_dist_10 " +
			"  from orderline_stock) " +
			"on conflict (s_w_id,s_i_id) do nothing";
		}

		boolean trace = LOG.isTraceEnabled(); 
		
		stockGetDistOrderId = this.getPreparedStatement(conn, stockGetDistOrderIdSQL);
		stockGetCountStock= this.getPreparedStatement(conn, stockGetCountStockSQL);
        migrateOrderLine1 = this.getPreparedStatement(conn, migrateOrderLineSQL1); 
        // migrateOrderLine2 = this.getPreparedStatement(conn, migrateOrderLineSQL2);
		migrateStack1 =this.getPreparedStatement(conn, migrateStackSQL1);
		// migrateStack2 =this.getPreparedStatement(conn, migrateStackSQL2);
		stmt = conn.createStatement();

		int threshold = TPCCUtil.randomNumber(10, 20, gen);
		int d_id = TPCCUtil.randomNumber(terminalDistrictLowerID,terminalDistrictUpperID, gen);

		int o_id = 0;
		// XXX int i_id = 0;
		int stock_count = 0;

		stockGetDistOrderId.setInt(1, w_id);
		stockGetDistOrderId.setInt(2, d_id);
		if (trace) LOG.trace(String.format("stockGetDistOrderId BEGIN [W_ID=%d, D_ID=%d]", w_id, d_id));
		ResultSet rs = stockGetDistOrderId.executeQuery();
		if (trace) LOG.trace("stockGetDistOrderId END");

		if (!rs.next()) {
			throw new RuntimeException("D_W_ID="+ w_id +" D_ID="+ d_id+" not found!");
		}
		o_id = rs.getInt("D_NEXT_O_ID");
		rs.close();

        migrateOrderLine1.setInt(1, w_id);
        migrateOrderLine1.setInt(2, d_id);
		migrateOrderLine1.setInt(3, o_id);
		migrateOrderLine1.setInt(4, o_id - 20);
		migrateOrderLine1.setInt(5, w_id);
		migrateOrderLine1.setInt(6, threshold);

		migrateStack1.setInt(1, w_id);
        migrateStack1.setInt(2, d_id);
		migrateStack1.setInt(3, o_id);
		migrateStack1.setInt(4, o_id - 20);
		migrateStack1.setInt(5, w_id);
		migrateStack1.setInt(6, threshold);

		conn.setAutoCommit(false);
		migrateOrderLine1.executeQuery();
		stmt.executeUpdate(migrateOrderLineSQL2);
		
		migrateStack1.executeQuery();
		stmt.executeUpdate(migrateStackSQL2);
		conn.commit();

		stockGetCountStock.setInt(1, w_id);
		stockGetCountStock.setInt(2, d_id);
		stockGetCountStock.setInt(3, o_id);
		stockGetCountStock.setInt(4, o_id - 20);
		stockGetCountStock.setInt(5, w_id);
		stockGetCountStock.setInt(6, threshold);
		if (trace) LOG.trace(String.format("stockGetCountStock BEGIN [W_ID=%d, D_ID=%d, O_ID=%d]", w_id, d_id, o_id));
		rs = stockGetCountStock.executeQuery();
		if (trace) LOG.trace("stockGetCountStock END");

		if (!rs.next()) {
			String msg = String.format("Failed to get StockLevel result for COUNT query " +
									"[W_ID=%d, D_ID=%d, O_ID=%d]", w_id, d_id, o_id);
			if (trace) LOG.warn(msg);
			throw new RuntimeException(msg);
		}
		stock_count = rs.getInt("STOCK_COUNT");
		if (trace) LOG.trace("stockGetCountStock RESULT=" + stock_count);

		conn.commit();
		rs.close();
		if (stmt != null) { stmt.close(); }

		if (trace) {
			StringBuilder terminalMessage = new StringBuilder();
			terminalMessage.append("\n+-------------------------- STOCK-LEVEL --------------------------+");
			terminalMessage.append("\n Warehouse: ");
			terminalMessage.append(w_id);
			terminalMessage.append("\n District:  ");
			terminalMessage.append(d_id);
			terminalMessage.append("\n\n Stock Level Threshold: ");
			terminalMessage.append(threshold);
			terminalMessage.append("\n Low Stock Count:       ");
			terminalMessage.append(stock_count);
			terminalMessage.append("\n+-----------------------------------------------------------------+\n\n");
			LOG.trace(terminalMessage.toString());
		}
		return null;
	}
}
