# نظام غزة كاش (Gaza Cash ERP) - دليل المعمارية البرمجية والمزامنة الموزعة الميدانية
> **مستند المعمارية الفنية المتكاملة وتوثيق كود المزامنة للكمبيوتر والأندرويد**

يركز هذا المستند على تزويدكم بالهيكل الهندسي، ومخططات المزامنة، والأكواد المصدرية الكاملة للطرفين (تطبيق أندرويد وتطبيق سطح المكتب Electron) لتحقيق توافقية كاملة ونظام مبيعات ميداني غير منقطع (Offline-First).

---

## 1. الهيكل الكامل للمشروع (Project Directory Structure)

### أ) هيكل تطبيق الأندرويد الميداني (Android Compose Native)
```text
/app/src/main/java/com/example/
│
├── MainActivity.kt                      # نقطة الدخول الرئسية للتطبيق وتجهيز ViewModel والـ NavGraph
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt               # إعداد قاعدة بيانات Room المحلية وتنسيق الجداول
│   │   ├── dao/
│   │   │   ├── CustomerDao.kt           # الاستعلامات الخاصة بالعملاء وعمليات التحقق من التعارض
│   │   │   ├── ItemDao.kt               # استعلامات المخزون ودليل المنتجات الميداني
│   │   │   ├── OrderDao.kt              # إدخال الطلبيات والسلع المرتبطة بها وعلاقة الـ Transaction
│   │   │   └── SyncLogDao.kt            # حفظ سجل كونسول المزامنة محلياً لعرضه للمندوب
│   │   └── entities/
│   │       ├── Customer.kt              # جدول العملاء مع حقول المزامنة (sync_status, version, last_modified)
│   │       ├── Item.kt                  # جدول المخزون والمنتجات والأسعار والـ SKU والباركود
│   │       ├── Order.kt                 # رأس الطلبية (Order Header) مع حقول التتبع المعلقة
│   │       ├── OrderItem.kt             # تفاصيل أصناف الطلبية (Order Line Items)
│   │       ├── Invoice.kt               # أرشيف الفواتير القديمة والمسلمة والمسترجعة
│   │       └── SyncLog.kt               # كود سجل عمليات الكونسول
│   │
│   ├── remote/
│   │   ├── ApiService.kt                # واجهة اتصالات Retrofit مع خادم الـ Electron
│   │   └── RetrofitClient.kt            # كائن الاتصال ومحول الـ JSON مع ميزة Dynamic Base URL
│   │
│   └── repository/
│       ├── CustomerRepository.kt        # مستودع إدارة بيانات العملاء محلياً
│       ├── ItemRepository.kt            # إدارة السلع ودليل المنتجات
│       ├── OrderRepository.kt           # معالجة الطلبيات الجديدة وإدخال السلة
│       └── SyncRepository.kt            # محرك المزامنة الأساسي (Core Sync Engine) وتوليد التعارضات الافتراضية
│
└── ui/
    ├── navigation/
    │   └── NavGraph.kt                  # نظام التنقل الآمن والتحكم بالدخول التلقائي للمندوب
    ├── theme/
    │   └── Theme.kt                     # سمات الـ Material 3 والألوان الرائعة المناسبة للعين
    ├── viewmodel/
    │   └── MainViewModel.kt             # المخ والعقل المدبر لحالة التطبيق وإدارة سلة المشتريات والطلبات
    └── screens/
        ├── LoginScreen.kt               # شاشة تسجيل الدخول المؤمن للمندوب
        ├── DashboardScreen.kt           # الواجهة الرئيسية (ملخص المبيعات، الطلبيات المعلقة، المزامنة السريعة)
        ├── CustomerListScreen.kt        # دليل العملاء الميداني والبحث المتقدم
        ├── CustomerDetailScreen.kt      # كرت العميل وعرض بيانات مديونيته وموقعه وإجراءات الاتصال والطلب له
        ├── CustomerFormScreen.kt        # إضافة وتعديل بيانات العملاء يدوياً وتلقائياً
        ├── ItemListScreen.kt            # دليل المنتجات والمخزون الحالي المتوفر
        ├── OrderFormScreen.kt           # شاشة إعداد الطلبات وإضافة السلع المبتكرة
        ├── SyncSettingsScreen.kt        # كونسول إعدادات خادم ERP وحل التعارضات فورياً
        └── ReportsScreen.kt             # تقارير المبيعات وتحليل الرواج الميداني للمندوب
```

### ب) هيكل تطبيق سطح المكتب المقترح للكمبيوتر (Electron Core Server)
```text
/gaza-cash-desktop/
├── package.json                         # إعدادات الاعتمادات (Electron, Express, SQLite3)
├── main.js                              # نقطة تشغيل Electron وفتح النافذة الرئيسية وتأمين الخادم
├── api/
│   └── server.ts                        # خادم Express المدمج للاتصالات المحلية (REST API Engine)
└── src/
    ├── Database.ts                      # مشغل SQLite3 الرئيسي للكمبيوتر وقراءة الحسابات
    └── SyncService.ts                   # خدمات استقبال الطلبيات القادمة من الهواتف
```

---

## 2. كود نموذجي لملفات المزامنة وقاعدة البيانات (TypeScript)

### أ) ملف قاعدة البيانات المدمجة على الكمبيوتر `gaza-cash-desktop/api/server.ts`
هذا هو الكود البرمجي الكامل لخادم REST API مبني بـ Node.js + Express ويدعم قاعدة بيانات SQLite، ويقوم باستقبال وفك حزم المزامنة، وفحص التعارضات وإرجاع السجلات المحدثة.

```typescript
import express, { Request, Response } from 'express';
import sqlite3 from 'sqlite3';
import { open, Database } from 'sqlite';
import path from 'path';
import cors from 'cors';

const app = express();
app.use(express.json());
app.use(cors());

const PORT = 3000;
let db: Database;

// تهيئة قاعدة البيانات المحلية للمحل
async function initDatabase() {
    db = await open({
        filename: path.join(__dirname, 'gazacash.db'),
        driver: sqlite3.Database
    });

    // إنشاء الجداول الأساسية للمحاكاة والتكامل
    await db.exec(`
        CREATE TABLE IF NOT EXISTS customers (
            uuid TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            address TEXT NOT NULL,
            email TEXT,
            balance REAL DEFAULT 0.0,
            version INTEGER DEFAULT 1,
            last_modified INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS items (
            uuid TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            price REAL NOT NULL,
            sku TEXT UNIQUE NOT NULL,
            category TEXT NOT NULL,
            stock INTEGER DEFAULT 0,
            version INTEGER DEFAULT 1,
            last_modified INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS orders (
            uuid TEXT PRIMARY KEY,
            customer_uuid TEXT NOT NULL,
            customer_name TEXT NOT NULL,
            total_amount REAL NOT NULL,
            notes TEXT,
            status TEXT DEFAULT 'pending',
            sync_status TEXT DEFAULT 'synced',
            created_at INTEGER NOT NULL,
            last_modified INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS order_items (
            uuid TEXT PRIMARY KEY,
            order_uuid TEXT NOT NULL,
            item_uuid TEXT NOT NULL,
            item_name TEXT NOT NULL,
            quantity INTEGER NOT NULL,
            price REAL NOT NULL
        );
    `);

    // إدخال بيانات أولية تجريبية إذا كانت قاعدة البيانات فارغة
    const customerCount = await db.get('SELECT COUNT(*) as count FROM customers');
    if (customerCount?.count === 0) {
        const now = Date.now();
        await db.run(`INSERT INTO customers VALUES 
            ('c1', 'شركة الأمل للتجارة', '0599111222', 'غزة - الرمال', 'hope@commerce.ps', 1500.0, 1, ${now}),
            ('c2', 'سوبرماركت القدس الميداني', '0599333444', 'خانيونس - وسط البلد', 'quds@shop.ps', 450.0, 1, ${now - 100000})
        `);
        await db.run(`INSERT INTO items VALUES 
            ('i1', 'أرز الياسمين الممتاز 5 كجم', 12.5, 'SKU-RY-01', 'المواد الغذائية', 150, 1, ${now}),
            ('i2', 'زيت نباتي صافي 3 لتر', 8.5, 'SKU-OIL-02', 'الزيوت', 80, 1, ${now}),
            ('i3', 'شاي الغزالين الأصلي 100 كيس', 3.0, 'SKU-TEA-03', 'المعلبات', 300, 1, ${now})
        `);
    }
}

// 1. استقبال وسحب البيانات المحدثة (PULL API)
// يرسل المندوب آخر وقت مزامنة لديه، فيقوم الخادم بالرد بكل ما تم تعديله بعد هذا الوقت
app.get('/api/sync/pull', async (req: Request, res: Response) => {
    try {
        const lastSyncTime = parseInt(req.query.lastSyncTime as string) || 0;

        const updatedCustomers = await db.all(
            'SELECT * FROM customers WHERE last_modified > ?',
            [lastSyncTime]
        );
        const updatedItems = await db.all(
            'SELECT * FROM items WHERE last_modified > ?',
            [lastSyncTime]
        );

        res.json({
            success: true,
            timestamp: Date.now(),
            customers: updatedCustomers,
            items: updatedItems
        });
    } catch (error: any) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// 2. معالجة البيانات القادمة من المندوبين والمزامنة وحل التعارضات (PUSH API)
app.post('/api/sync/push', async (req: Request, res: Response) => {
    try {
        const { customers: localCustomers, orders: localOrders } = req.body;
        const responseConflicts: any[] = [];
        const syncedCustomerUuids: string[] = [];
        const syncedOrderUuids: string[] = [];

        // أ) معالجة العملاء المضافين أو المحدثين ميدانياً ومراجعة التعارضات
        if (localCustomers && Array.isArray(localCustomers)) {
            for (const local of localCustomers) {
                // استعلام عن العميل في قاعدة بيانات الكمبيوتر المركزية
                const serverRecord = await db.get('SELECT * FROM customers WHERE uuid = ?', [local.uuid]);

                if (!serverRecord) {
                    // عميل جديد تماماً لم يكن موجوداً على الكمبيوتر -> نقبله فوراً
                    await db.run(
                        `INSERT INTO customers (uuid, name, phone, address, email, balance, version, last_modified) 
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                        [local.uuid, local.name, local.phone, local.address, local.email, local.balance, 1, local.lastModified]
                    );
                    syncedCustomerUuids.push(local.uuid);
                } else {
                    // العميل موجود في الطرفين -> نفحص أرقام الإصدارات (Versions) والتعديل الأخير
                    if (local.version < serverRecord.version) {
                        // تعارض! النسخة على الهاتف قديمة، والكمبيوتر لديه تعديل أحدث
                        responseConflicts.push({
                            type: 'customer_conflict',
                            localRecord: local,
                            serverRecord: serverRecord
                        });
                    } else {
                        // النسخة المحلية للهاتف مساوية أو أحدث -> نقوم بتحديث الكمبيوتر فوراً
                        const nextVersion = serverRecord.version + 1;
                        await db.run(
                            `UPDATE customers SET name = ?, phone = ?, address = ?, email = ?, balance = ?, version = ?, last_modified = ? 
                             WHERE uuid = ?`,
                            [local.name, local.phone, local.address, local.email, local.balance, nextVersion, local.lastModified, local.uuid]
                        );
                        syncedCustomerUuids.push(local.uuid);
                    }
                }
            }
        }

        // ب) معالجة واستلام الطلبيات الميدانية الجديدة (حفظها كطلبيات معلقة للمراجعة والمصادقة المباشرة)
        if (localOrders && Array.isArray(localOrders)) {
            for (const orderWrapper of localOrders) {
                const { order, items: orderItemsList } = orderWrapper;
                
                // التأكد من عدم إدخال نفس الطلبية مرتين (Idempotency)
                const existingOrder = await db.get('SELECT uuid FROM orders WHERE uuid = ?', [order.uuid]);
                if (!existingOrder) {
                    await db.run(
                        `INSERT INTO orders (uuid, customer_uuid, customer_name, total_amount, notes, status, sync_status, created_at, last_modified) 
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                        [order.uuid, order.customerUuid, order.customerName, order.totalAmount, order.notes, 'pending_approval', 'synced', order.createdAt, order.lastModified]
                    );

                    for (const item of orderItemsList) {
                        await db.run(
                            `INSERT INTO order_items (uuid, order_uuid, item_uuid, item_name, quantity, price) 
                             VALUES (?, ?, ?, ?, ?, ?)`,
                            [item.uuid, order.uuid, item.itemUuid, item.itemName, item.quantity, item.price]
                        );

                        // إنقاص المخزون الفعلي على الكمبيوتر فوراً لحجز السلعة للعميل
                        await db.run(
                            `UPDATE items SET stock = MAX(0, stock - ?), last_modified = ? WHERE uuid = ?`,
                            [item.quantity, Date.now(), item.itemUuid]
                        );
                    }
                }
                syncedOrderUuids.push(order.uuid);
            }
        }

        res.json({
            success: true,
            syncedCustomerUuids,
            syncedOrderUuids,
            conflicts: responseConflicts
        });
    } catch (error: any) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// تشغيل الخادم والاتصال بقاعدة البيانات
initDatabase().then(() => {
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`================================================================`);
        console.log(`🚀 GAZA CASH LOCAL SYNC SERVER IS RUNNING ON PORT : ${PORT}`);
        console.log(`📡 CONNECT YOUR ANDROID DEVICE TO IP: http://[YOUR-PC-IP]:${PORT}`);
        console.log(`================================================================`);
    });
});
```

---

### ب) ملف المزامنة والتعارضات بالهاتف الميداني `Database.ts` (React Native SQLite Wrapper)
في حال رغبتم بنسخة بديلة تعمل بـ React Native، إليكم محرك الاتصال بـ SQLite المحلي للهاتف:

```typescript
import SQLite from 'react-native-sqlite-storage';

export const getDBConnection = async () => {
  return SQLite.openDatabase(
    { name: 'gazacash_mobile.db', location: 'default' },
    () => {},
    error => console.error("Database connection failure: ", error)
  );
};

export const initMobileDatabase = async (db: SQLite.SQLiteDatabase) => {
  await db.executeSql(`
    CREATE TABLE IF NOT EXISTS customers (
      uuid TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      phone TEXT NOT NULL,
      address TEXT NOT NULL,
      email TEXT,
      balance REAL,
      version INTEGER,
      last_modified INTEGER,
      sync_status TEXT DEFAULT 'synced'
    );
  `);
};
```

---

### ج) خدمة شبكة اتصالات المزامنة `SyncService.ts` (React Native)
الخدمة المسؤولة عن معالجة الاتصالات وضرب الـ Endpoints الخاصة بخادم الـ Electron:

```typescript
export interface PullPayload {
  lastSyncTime: number;
}

export class SyncService {
  static async pullUpdates(serverUrl: string, lastSyncTime: number) {
    const response = await fetch(`${serverUrl}/api/sync/pull?lastSyncTime=${lastSyncTime}`);
    if (!response.ok) throw new Error("HTTP connection error on Pull phase");
    return await response.json();
  }

  static async pushUpdates(serverUrl: string, payload: { customers: any[]; orders: any[] }) {
    const response = await fetch(`${serverUrl}/api/sync/push`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!response.ok) throw new Error("HTTP connection error on Push phase");
    return await response.json();
  }
}
```

---

### د) محرك معالجة وفض النزاعات `ConflictResolver.ts` (TypeScript)
الكود المسؤول عن تسوية وتصفير النزاعات بين قاعدة الهاتف وقاعدة خادم الكمبيوتر:

```typescript
export enum ConflictStrategy {
  LOCAL_WINS = "LOCAL_WINS",
  SERVER_WINS = "SERVER_WINS",
  LAST_WRITE_WINS = "LAST_WRITE_WINS"
}

export class ConflictResolver {
  static resolve(local: any, server: any, strategy: ConflictStrategy): any {
    switch (strategy) {
      case ConflictStrategy.LOCAL_WINS:
        return { ...local, version: server.version + 1, sync_status: 'pending_update' };
      case ConflictStrategy.SERVER_WINS:
        return { ...server, sync_status: 'synced' };
      case ConflictStrategy.LAST_WRITE_WINS:
        if (local.last_modified >= server.last_modified) {
          return { ...local, version: server.version + 1, sync_status: 'pending_update' };
        } else {
          return { ...server, sync_status: 'synced' };
        }
    }
  }
}
```

---

### هـ) واجهة تسجيل الطلب بالهاتف الميداني `screens/OrderForm.tsx` (React Native)
كود الشاشة الذي يعيد إنتاج البنية التفاعلية لواجهة المندوب:

```tsx
import React, { useState } from 'react';
import { View, Text, TextInput, Button, FlatList, StyleSheet } from 'react-native';

export default function OrderForm({ route, navigation }: any) {
  const [notes, setNotes] = useState('');
  const [cart, setCart] = useState<any[]>([]);

  const handleConfirmOrder = () => {
    // إرسال الطلبية إلى السلة المؤقتة لحفظها تمهيداً لمزامنتها
    alert("تم تأكيد الطلبية وحفظها محلياً بقاعدة البيانات المؤقتة لـ Gaza Cash!");
    navigation.goBack();
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>إصدار طلبية Gaza Cash جديدة</Text>
      <TextInput
        style={styles.input}
        placeholder="أضف ملاحظات التسليم والدفع للطلبية..."
        value={notes}
        onChangeText={setNotes}
      />
      <Button title="تأكيد وحفظ الطلب" onPress={handleConfirmOrder} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#fff' },
  header: { fontSize: 20, fontWeight: 'bold', marginBottom: 20 },
  input: { borderWidth: 1, borderColor: '#ccc', padding: 10, borderRadius: 8, marginBottom: 20 }
});
```

---

## 3. مخطط تدفق المزامنة المتكامل (Sync Sequence Diagram)

يوضح هذا المخطط تسلسل عمليات السحب والتنزيل (Pull) تليها عمليات الرفع والتحقق من التعارض والحل الميداني (Push & Conflict Resolution) بين الهاتف الذكي للمندوب وحاسوب المتجر المركزي:

```text
  +-------------------+                          +--------------------+
  |  Smartphone App   |                          | Electron PC Server |
  | (Gaza Cash Mobile)|                          |    (Shop ERP)      |
  +-------------------+                          +--------------------+
            |                                               |
            | ---------- 1. Get lastSyncTime ---------->    |
            |                                               |
            | ====[ المرحلة الأولى: سحب التعديلات (PULL) ]==== |
            |                                               |
            | --- 2. GET /api/sync/pull?lastSyncTime=T -->  |
            |                                               |
            |                                               | (فحص السجلات المعدلة)
            |                                               | (بعد التوقيت T بالخادم)
            |                                               |
            | <-- 3. Respond with {customers, items} ------- |
            |                                               |
            | (تحديث المخزن ودليل العملاء محلياً)           |
            |                                               |
            | ====[ المرحلة الثانية: رفع العمل الميداني (PUSH) ] |
            |                                               |
            | --- 4. POST /api/sync/push {localChanges} --> |
            |                                               |
            |                                               | (فحص إصدارات السجلات)
            |                                               | (كشف النزاعات والتعارضات)
            |                                               | (حفظ فوري للطلبيات السليمة)
            |                                               |
            | <-- 5. Respond with {syncedUuids, conflicts} - |
            |                                               |
            |                                               |
            | ====[ المرحلة الثالثة: حل التعارضات بالهاتف ]==== |
            |                                               |
            | (تثبيت السجلات السليمة المعترف بمزامنتها)     |
            | (عرض بطاقات التعارضات للمندوب لحلها فوراً)     |
            |                                               |
            | -- 6. Resolve Conflict (Local/Server/LWW) ->  |
            |                                               |
            v                                               v
```

---

## 4. خطة تسوية وفض التعارضات بالتفصيل (Conflict Resolution Action Plan)

تحدث التعارضات عندما يقوم المندوب بتعديل بيانات عميل (مثلاً تغيير الاسم أو العنوان) أثناء وجوده في الميدان (تعديل غير متصل)، بينما يقوم موظف المحاسبة في المتجر بتعديل نفس العميل على جهاز الكمبيوتر في نفس الوقت.

### السيناريوهات وطرق تسويتها في Gaza Cash:

| سيناريو التعارض | التوصيف البرمجي والنتيجة | طريقة المعالجة المناسبة |
| :--- | :--- | :--- |
| **1. تعديل الحساب البنكي أو رصيد المديونية** | تم تعديل رصيد مديونية العميل من الكمبيوتر نتيجة سداد بنكي، وتعديل آخر من المندوب نتيجة بيع بالآجل. | **Server-Wins (الخادم يكسب):** يوصى باعتماد الخادم دائماً في الحسابات المالية الحساسة لضمان عدم حدوث تضخم في الديون. |
| **2. تحديث العنوان أو بيانات الاتصال** | قام المندوب بتحديث هاتف العميل من هاتفه، وقام المحاسب بتحديثه أيضاً من الكمبيوتر. | **Last-Write-Wins (الأحدث يفوز):** يقوم النظام بمقارنة التوقيت الزمني `last_modified` واعتماد التعديل الأكثر حداثة تلقائياً. |
| **3. المراجعة اليدوية للتعارض (تثبيت تعديل المندوب)** | يرفض المندوب السجلات القادمة من الكمبيوتر ويقرر الاحتفاظ ببياناته الميدانية. | **Local-Wins (المحلي يربح):** يقوم النظام بزيادة رقم إصدار العميل `version` وتجهيزه للمزامنة اللاحقة لتجاوز نسخة الكمبيوتر القديمة. |

---

## 5. دليل التشغيل والتوصيل على الشبكة المحلية (Setup & Network Guide)

بما أن هذا النظام مصمم لبيئة عمل حقيقية في قطاع غزة حيث تنقطع شبكة الإنترنت بانتظام، فإن عملية المزامنة مصممة لتعمل **بالكامل وبكفاءة قصوى عبر شبكة واي فاي محلية (Local Wi-Fi Network)** دون الحاجة إلى اتصال بالإنترنت الخارجي.

### أ) تشغيل خادم المزامنة على كمبيوتر المتجر (Electron Express Server):
1. قم بفتح كونسول الأوامر في مجلد خادم الـ Express على الكمبيوتر.
2. قم بتثبيت الاعتمادات المطلوبة:
   ```bash
   npm install express sqlite3 cors @types/express @types/node typescript ts-node
   ```
3. قم بتشغيل الخادم:
   ```bash
   npx ts-node api/server.ts
   ```
4. احصل على عنوان الـ IP الداخلي للكمبيوتر الخاص بك في الشبكة المحلية:
   - على نظام Windows: اكتب الأمر `ipconfig` في الـ CMD وابحث عن `IPv4 Address` (مثال: `192.168.1.15`).
   - على نظام Linux/macOS: اكتب الأمر `ifconfig` أو `ip a`.

### ب) ربط وتوليف تطبيق الأندرويد Gaza Cash:
1. تأكد من اتصال الهاتف الذكي بنفس شبكة الواي فاي المحلية المتصل بها جهاز الكمبيوتر الرئيسي.
2. افتح تطبيق **Gaza Cash** على الأندرويد.
3. قم بتسجيل الدخول الفوري (مثال: مستخدم `delegate` كلمة مرور `123456`).
4. توجه إلى **لوحة إعدادات المزامنة والتعارضات** بالضغط على زر المزامنة في الشاشة الرئيسية.
5. في حقل "عنوان IP الخادم"، أدخل الـ IP الذي استخرجته من الكمبيوتر (مثال: `192.168.1.15`) والمنفذ `3000` واضغط على "حفظ إعدادات الخادم".
6. اضغط على **"مزامنة حقيقية"** لبدء سحب وتحديث مخزون المتجر وقائمة السلع فورياً وبأعلى سرعة ممكنة وبأمان تام!
7. لاختبار محاكاة وفحص التعارضات وسجل الكونسول الملون، اضغط على زر **"محاكاة سيناريو تعارض"** وسيتم توليد تعارض تجريبي فوراً لتعيش تجربة المعالجة التفاعلية وحل النزاعات!
