# Kotlin/Java Code Generator 程式碼產生器

這個專案提供多種 Kotlin/Java 的程式碼產生功能，雖然現在只有 EntityCodeGenerator 而且只能產 Kotlin :laughing:，未來會有更多的產生器 :muscle:

## Entity Code Generator

1. 在項目根目錄中執行 Gradle 指令，可以產生一個 jar 檔案。預設情況下，這個檔案會被放置在 build/libs 路徑底下。

   ```cmd

    ./gradlew build

   ```

2. 建立檔案類型為 txt 的 Entity 的規格檔案。內容如下，這個檔案的名稱將會做為產生的 Entity 類別名稱

   - 範例

      ![image](https://user-images.githubusercontent.com/70104159/230525153-80e4a126-69d7-44a7-8f3a-ccaf0bccf074.png)

   - 規格檔案的欄位必須依照以下由左至右的順序宣告，資料間隔符號可以透過 `delimiter` 參數指定，預設為 tab

      |  Key | Name | Type  | Nullable | Description |
      |  ----  | ---- | ----  | ----  | ----  |
      | PK 或 UK  | 欄位名稱 | 欄位類型 | 是否可為空，notnull 或 null |說明|

3. 支援項目

   - 可用參數

      |  參數名 | 參數類型 | 多參或單參  | 必填 | 預設 | 說明  | 錯誤訊息  |
      |  ----  | ---- | ----  | ----  | ----  | ----  | ---- |
      | source  | String | 多 | Y |-|Entity 的規格 txt 檔案路徑，支援 Windows/Linux/Mac 的檔案路徑| Please declare source|
      | destination | String | 單 | N | 規格檔路徑中與規格檔名稱相同的資料夾下| 產生 Entity 的檔案存放位置，若沒有指定，將會在 txt 檔案路徑新增一個同名的 folder，產生的程式碼將會存放在這個 folder|-|
      | delimiter | String | 單 | N | \t |Entity 規格檔案的資料間隔符號 |-|
      | enversAudit | Boolean | 單 | N | false | 是否產生 @Audited 註解和相關引用訊息 |-|

   - 類型對應
      |  Postgres | Kotlin  |
      |  ----  | ----  |
      |  UUID | UUID  |
      |  JSONB | I18nNameMap  |
      |  BOOL | Boolean  |
      |  TIMESTAMP | LocalDateTime  |
      |  DATETIME | LocalDateTime  |
      |  TEXT | String  |
      |  TIME | LocalTime  |
      |  INT | Int  |
      |  default | String  |
   - 鍵
      |  Key | 是否支援複合鍵  | 說明 |
      |  ----  | ----  | ----  |
      |  PK | Y  |若為複合主鍵，產生對應的 PrimaryKey Class|
      |  UK | Y  |若為複合鍵，在 `@Table` 加上 `uniqueConstraints = [UniqueConstraint(columnNames = [field1, field2])])`|
   - 註解
      | Target |  Annotation | 說明 |
      |----|  ----  | ----  |
      |  CLASS  |  `@Audited` | |
      |  CLASS  |  `@Entity` |  |
      |  CLASS  |  `@Table` |   |
      |  CLASS  |  `@EntityListeners` | 有更新時間、更新使用者、創建時間、創建使用者時會加上此註解 |
      |  CLASS  |  `@IdClass` | 有複合主鍵時會加上此註解|
      |  CLASS  |  `@TypeDefs` | 有 `jsonb` 型別時會加上此註解  |
      |  FIELD  |  `@Column` | 所有欄位皆會加上此註解 ，不可為空時加上 `nullable = false`，創建時間、創建使用者加上 `updatable = false`，單一 UK 加上 `unique = true`，jsonb 欄位加上 `columnDefinition = "jsonb"` |
      |  FIELD  |  `@LastModifiedDate` |更新時間欄位加上此註解 |
      |  FIELD  |  `@LastModifiedBy` |更新使用者欄位加上此註解 |
      |  FIELD  |  `@CreatedDate` |創建時間欄位加上此註解 |
      |  FIELD  |  `@CreatedBy` |創建使用者欄位加上此註解 |

4. 使用範例: 在 build/libs 路徑下執行以下指令

   - 通用格式: {version} 版號、{file}: 規格檔案路徑

      ```cmd

            java -jar jmonster-{version}.jar --source="{file}"

      ```

   - 單一檔案，未指定存放位置

      ```cmd
      
      java -jar jmonster-{version}.jar --source="C:\path\to\file\Shiba.txt" 

      ```

   - 單一檔案，指定存放位置

      ```cmd

      java -jar jmonster-{version}.jar --source="C:\path\to\file\Shiba.txt" --destination="C:\path\to\entity\cute\animal" 

      ```

   - 多檔案，未指定存放位置

      ```cmd

      java -jar jmonster-{version}.jar --source="C:\path\to\file\Shiba.txt"   --source="C:\path\to\file\Cat.txt" 

      ```

   - 多檔案，指定存放位置

      ```cmd

      java -jar jmonster-{version}.jar \ 
      --source="C:\path\to\file\Shiba.txt" \ 
      --source="C:\path\to\file\Cat.txt" \ 
      --destination="C:\path\to\entity\cute\animal" 

      ```
