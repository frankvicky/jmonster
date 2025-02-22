package org.jmonster.codegenerator.common.generator

import org.jmonster.codegenerator.common.model.dto.common.RestDto
import org.jmonster.codegenerator.common.model.dto.entity.Column
import org.jmonster.codegenerator.common.model.dto.entity.EntityCodeGenerateRequestDto
import org.springframework.boot.ApplicationArguments
import java.io.File
import java.nio.file.Files
import java.nio.file.Path


class EntityCodeGenerator : CodeGenerator {

    private var source: List<String> = listOf()
    private var destination: String = ""
    private var delimiter: String = ""
    private var packageDeclaration: String = ""
    private var contentDto: MutableMap<String, List<Column>> = mutableMapOf()
    private var enversAudit: Boolean = false
    private val metaColumns = listOf("created_date", "created_user_id", "updated_date", "updated_user_id")
    private fun List<Column>.containsMetaColumn() = this.any { metaColumns.contains(it.name) }
    private val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    private val snakeRegex = "_[a-zA-Z]".toRegex()
    private var uniqueColumnCount: MutableMap<String, Int> = mutableMapOf()

    private fun composeCode(
        className: String,
        vararg unitGenerator: ((className: String) -> String),
    ): String {
        val code = StringBuilder()
        unitGenerator.forEach { it ->
            code.appendLine(it(className))
        }

        return code.toString()

    }

    private fun generatePackageCode(className: String): String {
        return "package $packageDeclaration"
    }

    private fun generateImportCode(className: String): String {

        val thisContent = contentDto[className] ?: throw Exception("Content Not Found")

        val importCode = StringBuilder()
        if (enversAudit) {
            importCode.appendLine("import org.hibernate.envers.Audited")
        }
        importCode.appendLine("import java.util.*").appendLine("import javax.persistence.*")

        if (thisContent.containsMetaColumn()) importCode.appendLine("import org.springframework.data.jpa.domain.support.AuditingEntityListener")
        if (thisContent.map { it.type }
                .any { type -> type.equals("timestamp", true) }) importCode.appendLine("import java.time.LocalDateTime")

        val columnsName = thisContent.map { it.name }
        if (columnsName.contains("updated_date")) importCode.appendLine("import org.springframework.data.annotation.LastModifiedDate")
        if (columnsName.contains("updated_user_id")) importCode.appendLine("import org.springframework.data.annotation.LastModifiedBy")
        if (columnsName.contains("created_date")) importCode.appendLine("import org.springframework.data.annotation.CreatedDate")
        if (columnsName.contains("created_user_id")) importCode.appendLine("import org.springframework.data.annotation.CreatedBy")
        if (thisContent.any { it.type.contains("JSONB", true) }) {
            importCode.appendLine("import org.hibernate.annotations.Type")
                .appendLine("import org.hibernate.annotations.TypeDef")
                .appendLine("import org.hibernate.annotations.TypeDefs")
                .appendLine("import com.vladmihalcea.hibernate.type.json.JsonBinaryType")
        }

        return importCode.toString()
    }

    private fun generateAnnotationCode(className: String): String {

        val thisContent = contentDto[className] ?: throw Exception("Content Not Found")

        val annotationCode = StringBuilder().appendLine("@Entity")

        if (enversAudit) {
            annotationCode.appendLine("@Audited")
        }

        val uniqueColumns = thisContent.filter { it.key.equals("UK", true) }

        if ((uniqueColumnCount[className] ?: 0) > 1) {
            val uniqueColumnsString = uniqueColumns.joinToString(",") { "\"${it.name}\"" }
            annotationCode.appendLine("@Table(name = \"tbl_${className.camelToSnakeCase()}\", uniqueConstraints = [UniqueConstraint(columnNames = [$uniqueColumnsString])])")
        } else {
            annotationCode.appendLine("@Table(name = \"tbl_${className.camelToSnakeCase()}\")")
        }

        if (thisContent.containsMetaColumn()) annotationCode.appendLine("@EntityListeners(AuditingEntityListener::class)")
        if ((thisContent.groupBy { it.key }["PK"]?.size ?: 0) > 1) {
            annotationCode.appendLine("@IdClass(${className}PrimaryKey::class)")
        }
        if (thisContent.any { it.type.contains("JSONB", true) }) {
            annotationCode.appendLine("@TypeDefs(TypeDef(name = \"jsonb\", typeClass = JsonBinaryType::class))")
        }

        return annotationCode.toString()

    }

    private fun generateConstructorCode(className: String): String {
        val constructorCode = StringBuilder()
        constructorCode.appendLine("data class ${className}(")
        contentDto[className]?.filter { !metaColumns.contains(it.name) }
            ?.forEach { constructorCode.appendLine(getColumnDef(className, it)) }
        constructorCode.appendLine(")")
        return constructorCode.toString()
    }

    private fun generateBodyCode(className: String): String {
        val bodyCode = StringBuilder()
        bodyCode.appendLine("{")
        contentDto[className]?.filter { metaColumns.contains(it.name) }
            ?.forEach { bodyCode.appendLine(getMetaColumnDef(className, it)) }
        bodyCode.appendLine("}").appendLine()
        return bodyCode.toString()

    }

    private fun generatePkCode(className: String): String {
        val pkCode = StringBuilder()
        val map = contentDto[className]?.groupBy { it.key } ?: emptyMap()
        if ((map["PK"]?.size ?: 0) > 1) {
            pkCode.appendLine("class ${className}PrimaryKey(")
            map["PK"]!!.forEach { pkCode.appendLine(getPKColumn(it)) }
            pkCode.appendLine(") : Serializable {").appendLine(getCompositePKConstructor2(map["PK"]!!)).appendLine("}")
        }
        return pkCode.toString()

    }

    private fun convertType(dbType: String): String {

        return when (dbType.uppercase()) {
            "UUID" -> "UUID"
            "JSONB(I18NNAMEMAP)" -> "I18nNameMap"
            "BOOL" -> "Boolean"
            "TIMESTAMP" -> "LocalDateTime"
            "DATETIME" -> "LocalDateTime"
            "DATE" -> "LocalDate"
            "TIME" -> "LocalTime"
            "TEXT" -> "String"
            "INT" -> "Int"
            "BIGINT" -> "Long"
            "DOUBLE PRECISION", "FLOAT8" -> "Double"
            "DECIMAL" -> "BigDecimal"
            "REAL" -> "Float"
            "BYTEA" -> "ByteArray"
            "ENUM" -> "String"
            "JSONB(LIST)" -> "List"
            "JSONB(MAP)" -> "Map"
            else -> {
                if (dbType.uppercase().contains("VARCHAR")) {
                    "String"
                } else if (dbType.uppercase().contains("NUMERIC") || dbType.uppercase().contains("DECIMAL")) {
                    "BigDecimal"
                } else {
                    "String"
                }
            }
        }

    }

    private fun getColumnDef(className: String, column: Column): String {
        val columnBlock = StringBuilder()
        columnBlock.appendLine(if (column.key.equals("PK", true)) "@Id" else "")
            .appendLine(if (column.type.uppercase().contains("JSONB", true)) "@Type(type = \"jsonb\")" else "")
            .appendLine(getColumn(className, column)).appendLine(getField(column))
        return columnBlock.toString()
    }

    private fun getColumn(className: String, column: Column): String {
        buildList {
            addNameDef(column)
            addVarcharLengthDef(column)
            addNumericPrecisionAndScaleDef(column)
            addNullableDef(column)
            addUpdatableDef(column)
            addUniqueDef(className, column)
            addJsonbColumnDef(column)
        }.let {
            return "@Column(${it.joinToString(", ")})"
        }
    }

    private fun MutableList<String>.addNameDef(column: Column) {
        this.add("name = \"${column.name}\"")
    }

    private fun MutableList<String>.addVarcharLengthDef(column: Column) {
        if (column.type.contains("varchar", true)) {
            val regex = Regex("(\\d+)")
            regex.find(column.type)?.let {
                val (length) = it.groupValues.drop(0)
                this.add("length=$length")
            }
        }
    }

    private fun MutableList<String>.addNumericPrecisionAndScaleDef(column: Column) {
        if (column.type.contains("decimal", true) || column.type.contains("numeric", true)) {
            val regex = Regex("(\\d+),(\\d+)")
            regex.find(column.type)?.let {
                val values = it.groupValues
                val precision = values[1]
                val scale = values[2]
                this.add("precision=$precision")
                this.add("scale=$scale")
            }
        }
    }

    private fun MutableList<String>.addNullableDef(column: Column) {
        if (column.nullable.equals("notnull", true) && column.key != "PK") this.add("nullable = false")
    }

    private fun MutableList<String>.addUpdatableDef(column: Column) {
        if (column.name.equals("created_date", true) || column.name.equals(
                "created_user_id", true
            )
        ) {
            this.add("updatable = false")
        }
    }

    private fun MutableList<String>.addUniqueDef(className: String, column: Column) {
        if (uniqueColumnCount[className] == 1 && column.key.equals("UK", true)) {
            this.add("unique = true")
        }
    }

    private fun MutableList<String>.addJsonbColumnDef(column: Column) {
        if (column.type.uppercase().contains("JSONB", true)) {
            this.add("columnDefinition = \"jsonb\"")
        }
    }

    private fun getField(column: Column): String {
        val notNull = column.nullable.equals("notnull", true)
        return "var ${column.name.snakeToLowerCamelCase()}: ${convertType(column.type)}${if (notNull) "" else "?"},"
    }

    private fun getMetaColumnDef(className: String, column: Column): String {
        val code = StringBuilder()
        when (column.name) {
            "updated_date" -> code.appendLine("@LastModifiedDate")
            "updated_user_id" -> code.appendLine("@LastModifiedBy")
            "created_date" -> code.appendLine("@CreatedDate")
            "created_user_id" -> code.appendLine("@CreatedBy")
        }
        code.appendLine(getColumn(className, column)).appendLine("lateinit ${getField(column)}".replace(",", ""))

        return code.toString()
    }

    private fun getPKColumn(column: Column) =
        "var ${column.name.snakeToLowerCamelCase()}: ${convertType(column.type)},"

    private fun getCompositePKConstructor2(column: List<Column>) =
        "constructor() : this(${column.joinToString(", ") { convertToConstructorParam(it.type) }})"

    private fun convertToConstructorParam(dbType: String): String {
        return when (dbType.uppercase()) {
            "UUID" -> "UUID.randomUUID()"
            "JSONB" -> "\"\""
            "BOOL" -> "false"
            "TIMESTAMP" -> "LocalDateTime.now()"
            else -> {
                "\"\""
            }
        }
    }


    fun String.camelToKebabCase(): String {

        return camelRegex.replace(this) {
            "-${it.value}"
        }.lowercase()
    }

    private fun String.camelToSnakeCase(): String {
        return camelRegex.replace(this) {
            "_${it.value}"
        }.lowercase()
    }

    private fun String.snakeToLowerCamelCase(): String {
        return snakeRegex.replace(this) {
            it.value.replace("_", "").uppercase()
        }
    }

    override fun generate(args: ApplicationArguments?) {
        if (args == null) {
            println("Generate NOTHING")
            return
        }
        if (!preCheck(args)) return
        output()
    }

    override fun generate(dto: RestDto): File {
        if (dto !is EntityCodeGenerateRequestDto) throw Exception("ERR_UNKNOWN")

        // [0] -> PK or UK or empty
        // [1] -> name
        // [2] -> type
        // [3] -> null or nonnull
        // [4] -> description

        enversAudit = dto.enversAudit
        contentDto[dto.tableName] = dto.columns
        uniqueColumnCount[dto.tableName] = dto.columns.filter { it.key.equals("UK", true) }.size

        val code = composeCode(
            dto.tableName,
            this::generatePackageCode,
            this::generateImportCode,
            this::generateAnnotationCode,
            this::generateConstructorCode,
            this::generateBodyCode,
            this::generatePkCode
        )

        //TODO generate code in other languages
        val tempFile: Path = Files.createTempFile(dto.tableName, ".kt")
        Files.write(tempFile, listOf(code))
        return tempFile.toFile()

    }

    override fun generate(tableName: String, text: String): File {
        // [0] -> PK or UK or empty
        // [1] -> name
        // [2] -> type
        // [3] -> null or nonnull
        // [4] -> description
        // TODO complete
        val contentArg = text.split("\\r?\\n").map { line -> line.split(delimiter).map { it.trim() } }
        contentDto[tableName] = contentArg.map { line ->
            Column(
                key = line[0].trim(),
                name = line[1].trim(),
                type = line[2].trim(),
                nullable = line[3].trim(),
                description = line[4].trim()
            )
        }
        uniqueColumnCount[tableName] = contentArg.filter { it[0].equals("UK", true) }.size

        val code = composeCode(
            tableName,
            this::generatePackageCode,
            this::generateImportCode,
            this::generateAnnotationCode,
            this::generateConstructorCode,
            this::generateBodyCode,
            this::generatePkCode
        )

        //TODO generate code in other languages
        val tempFile: Path = Files.createTempFile(tableName, ".kt")
        Files.write(tempFile, listOf(code))
        return tempFile.toFile()
    }

    private fun output() {

        source.forEach { s ->

            val separator = File.separator

            val file = File(s)
            val thisClassName = file.name.split(".")[0]
            var thisDestination = destination.ifBlank {
                s.substring(
                    0, s.lastIndexOf(separator)
                ) + "$separator$thisClassName$separator"
            }

            if (thisDestination[thisDestination.lastIndex].toString() != separator) thisDestination += separator

            if (destination.isBlank()) File(thisDestination).mkdirs()


            // [0] -> PK or UK or empty
            // [1] -> name
            // [2] -> type
            // [3] -> null or nonnull
            // [4] -> description
            val contentArg = file.readLines().map { line -> line.split(delimiter).map { it.trim() } }
            contentDto[thisClassName] = file.readLines().map { line ->
                val def = line.split(delimiter)
                Column(
                    key = def[0].trim(),
                    name = def[1].trim(),
                    type = def[2].trim(),
                    nullable = def[3].trim(),
                    description = def[4].trim()
                )
            }
            uniqueColumnCount[thisClassName] = contentArg.filter { it[0].equals("UK", true) }.size

            val code = composeCode(
                thisClassName,
                this::generatePackageCode,
                this::generateImportCode,
                this::generateAnnotationCode,
                this::generateConstructorCode,
                this::generateBodyCode,
                this::generatePkCode
            )


            File("${thisDestination}${thisClassName}.kt").bufferedWriter().use { out ->
                code.lines().forEach {
                    out.write(it)
                    out.newLine()
                }
            }

            println("Generated Entity: ${thisClassName}.kt")

        }
    }

    private fun preCheck(args: ApplicationArguments): Boolean {
        val sourceValid = preCheckSource(args)
        if (!sourceValid) return false
        val destinationValid = preCheckDestination(args)
        if (!destinationValid) return false
        preCheckDelimiter(args)
        preCheckPackage(args)
        preCheckEnverAudit(args)
        return true
    }

    private fun preCheckSource(args: ApplicationArguments): Boolean {
        source = args.getOptionValues("source")
        return if (source.isEmpty()) {
            println("Please declare --source")
            false
        } else {
            true
        }
    }

    private fun preCheckDestination(args: ApplicationArguments): Boolean {
        val destinationArgs = args.getOptionValues("destination")
        return if (destinationArgs != null && destinationArgs.size > 1) {
            println("Only one destination allowed")
            false
        } else {
            destination = destinationArgs?.firstOrNull() ?: ""
            true
        }
    }

    private fun preCheckPackage(args: ApplicationArguments): Boolean {
        val packageArgs = args.getOptionValues("package")
        packageDeclaration = packageArgs?.firstOrNull() ?: ""
        return true
    }

    private fun preCheckDelimiter(args: ApplicationArguments): Boolean {
        val delimiterArg = args.getOptionValues("delimiter")
        delimiter = if (delimiterArg.isNullOrEmpty()) "\t" else delimiterArg.first()
        return true
    }

    private fun preCheckEnverAudit(args: ApplicationArguments): Boolean {
        val enversAuditArg = args.getOptionValues("enversAudit")
        enversAudit = enversAuditArg?.firstOrNull()?.toBoolean() ?: false
        return true
    }

}



