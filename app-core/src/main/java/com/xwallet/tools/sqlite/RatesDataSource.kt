/**
 * BreadWallet
 *
 * Created by Mihail Gutan <mihail@breadwallet.com> on 9/25/15.
 * Copyright (c) 2016 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.xwallet.tools.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.xwallet.breadbox.BreadBox
import com.xwallet.legacy.presenter.entities.CurrencyEntity
import com.xwallet.tools.manager.BRReportsManager
import com.xwallet.tools.util.BRConstants
import com.xwallet.tools.util.Utils
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein
import org.kodein.di.erased.instance
import java.util.ArrayList
import java.util.Locale

class RatesDataSource private constructor(context: Context) : com.xwallet.tools.sqlite.BRDataSourceInterface, KodeinAware {

    // Database fields
    private var database: SQLiteDatabase? = null
    private val dbHelper = com.xwallet.tools.sqlite.BRSQLiteHelper.getInstance(context)
    private val allColumns = arrayOf(
        com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_CODE,
        com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_NAME,
        com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_RATE,
        com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO
    )

    override val kodein by closestKodein { context }
    private val breadBox by instance<BreadBox>()

    fun putCurrencies(currencyEntities: Collection<CurrencyEntity>): Boolean {
        if (currencyEntities.isEmpty()) {
            Log.e(TAG, "putCurrencies: failed: $currencyEntities")
            return false
        }
        return try {
            database = openDatabase()
            database!!.beginTransaction()
            var failed = 0
            for (c in currencyEntities) {
                val code = c.code.toUpperCase(Locale.ROOT)
                val iso = c.iso.toUpperCase(Locale.ROOT)
                val values = ContentValues()
                if (com.xwallet.tools.util.Utils.isNullOrEmpty(code) || c.rate <= 0) {
                    failed++
                    continue
                }
                values.put(com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_CODE, code)
                values.put(com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_NAME, c.name)
                values.put(com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_RATE, c.rate)
                values.put(com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO, iso)
                database!!.insertWithOnConflict(
                    com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            if (failed != 0) {
                Log.e(TAG, "putCurrencies: failed:$failed")
            }
            database!!.setTransactionSuccessful()
            true
        } catch (ex: Exception) {
            Log.e(TAG, "putCurrencies: failed: ", ex)
            BRReportsManager.reportBug(ex)

            //Error in between database transaction
            false
        } finally {
            database!!.endTransaction()
            closeDatabase()
        }
    }

    fun deleteAllCurrencies(app: Context?, iso: String) {
        try {
            database = openDatabase()
            database!!.delete(
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO + " = ?",
                arrayOf(iso.toUpperCase(Locale.ROOT))
            )
        } finally {
            closeDatabase()
        }
    }

    fun getAllCurrencies(iso: String): List<CurrencyEntity> {
        val currencies: MutableList<CurrencyEntity> = ArrayList()
        var cursor: Cursor? = null
        try {
            database = openDatabase()
            cursor = database!!.query(
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                allColumns,
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO + " = ? COLLATE NOCASE",
                arrayOf(iso.toUpperCase(Locale.ROOT)),
                null,
                null,
                "\'" + com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_CODE + "\'"
            )
            cursor.moveToFirst()
            val system = breadBox.getSystemUnsafe()
            while (!cursor.isAfterLast) {
                val curEntity = cursorToCurrency(cursor)
                val isCrypto = system?.wallets?.none {
                    it.currency.code.equals(curEntity.code, true)
                } ?: true
                if (!isCrypto) {
                    currencies.add(curEntity)
                }
                cursor.moveToNext()
            }
            // make sure to close the cursor
        } finally {
            cursor?.close()
            closeDatabase()
        }
        Log.e(TAG, "getAllCurrencies: size:" + currencies.size)
        return currencies
    }

    fun getAllCurrencyCodes(app: Context?, iso: String): List<String> {
        val ISOs: MutableList<String> = ArrayList()
        var cursor: Cursor? = null
        try {
            database = openDatabase()
            cursor = database!!.query(
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                allColumns, com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO + " = ? COLLATE NOCASE",
                arrayOf(iso.toUpperCase(Locale.ROOT)),
                null, null, null
            )
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val curEntity = cursorToCurrency(cursor)
                ISOs.add(curEntity.code)
                cursor.moveToNext()
            }
            // make sure to close the cursor
        } finally {
            cursor?.close()
            closeDatabase()
        }
        return ISOs
    }

    @Synchronized
    fun getCurrencyByCode(iso: String, code: String): CurrencyEntity? {
        var cursor: Cursor? = null
        var result: CurrencyEntity? = null
        return try {
            database = openDatabase()
            cursor = database!!.query(
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                allColumns,
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_CODE + " = ? AND " + com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_ISO + " = ? COLLATE NOCASE",
                arrayOf(code.toUpperCase(Locale.ROOT), iso.toUpperCase(Locale.ROOT)),
                null,
                null,
                null
            )
            cursor.moveToNext()
            if (!cursor.isAfterLast) {
                result = cursorToCurrency(cursor)
            }
            result
        } finally {
            cursor?.close()
            closeDatabase()
        }
    }

    private fun printTest() {
        var cursor: Cursor? = null
        try {
            database = openDatabase()
            val builder = StringBuilder()
            cursor = database!!.query(
                com.xwallet.tools.sqlite.BRSQLiteHelper.CURRENCY_TABLE_NAME,
                allColumns, null, null, null, null, null
            )
            builder.append("Total: ${cursor.count}")
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val ent = cursorToCurrency(cursor)
                builder.append("Name: ${ent.name}, code: ${ent.code}, rate: ${ent.rate}, iso: ${ent.iso}")
                cursor.moveToNext()
            }
            Log.e(TAG, "printTest: $builder")
        } finally {
            cursor?.close()
            closeDatabase()
        }
    }

    private fun cursorToCurrency(cursor: Cursor?): CurrencyEntity {
        return CurrencyEntity(cursor!!.getString(0), cursor.getString(1), cursor.getFloat(2), cursor.getString(3))
    }

    override fun openDatabase(): SQLiteDatabase {
//        if (mOpenCounter.incrementAndGet() == 1) {
        // Opening new database
        if (database == null || !database!!.isOpen) database = dbHelper.writableDatabase
        dbHelper.setWriteAheadLoggingEnabled(BRConstants.WRITE_AHEAD_LOGGING)
        //        }
//        Log.d("Database open counter: ",  String.valueOf(mOpenCounter.get()));
        return database!!
    }

    override fun closeDatabase() {
//        if (mOpenCounter.decrementAndGet() == 0) {
//            // Closing database
//        database.close();
//onChanged
//        }
//        Log.d("Database open counter: " , String.valueOf(mOpenCounter.get()));
    }

    companion object {
        private val TAG = RatesDataSource::class.java.name
        private var instance: RatesDataSource? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): RatesDataSource {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = RatesDataSource(context)
                    }
                }
            }
            return instance!!
        }
    }
}
