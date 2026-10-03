package com.example.foldertotxt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var selectButton: Button
    private lateinit var convertButton: Button

    private var selectedFolderUri: Uri? = null
    private var totalFiles = 0
    private var totalFolders = 0
    private var successFiles = 0
    private var failedFiles = 0

    companion object {
        private const val PERMISSION_REQUEST = 100
        private const val FOLDER_PICKER_REQUEST = 101
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        selectButton = findViewById(R.id.selectButton)
        convertButton = findViewById(R.id.convertButton)

        selectButton.setOnClickListener { requestAccess() }

        convertButton.setOnClickListener {
            if (selectedFolderUri != null) {
                convertFolderToTxt()
            } else {
                Toast.makeText(this, "Sélectionnez un dossier", Toast.LENGTH_SHORT).show()
            }
        }

        convertButton.isEnabled = false
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST
                )
            } else {
                openFolderPicker()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST
                )
            } else {
                openFolderPicker()
            }
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, FOLDER_PICKER_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFolderPicker()
            } else {
                Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FOLDER_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val folderName = uri.lastPathSegment?.split(":")?.last() ?: "Dossier"
                selectedFolderUri = uri
                statusTextView.text = "Dossier sélectionné: $folderName"
                convertButton.isEnabled = true
            }
        }
    }

    private fun convertFolderToTxt() {
        totalFiles = 0
        totalFolders = 0
        successFiles = 0
        failedFiles = 0

        Thread {
            try {
                runOnUiThread {
                    statusTextView.text = "Conversion en cours...\nAnalyse des fichiers..."
                }

                val rootDocumentFile = DocumentFile.fromTreeUri(this, selectedFolderUri!!)
                    ?: throw IllegalStateException("Impossible d'ouvrir le dossier sélectionné")

                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "FolderToTxt"
                )
                outputDir.mkdirs()

                val outputFile = File(
                    outputDir,
                    "fusion_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
                )

                val builder = StringBuilder()
                builder.append("╔════════════════════════════════════════════╗\n")
                builder.append("║   RAPPORT DE CONVERSION DE DOSSIER EN TXT  ║\n")
                builder.append("╚════════════════════════════════════════════╝\n\n")
                builder.append("Mode: Tous les fichiers (tous les types)\n")
                builder.append("Date de conversion: ${Date()}\n")
                builder.append("Dossier source: ${rootDocumentFile.name}\n")
                builder.append("====================================\n\n")

                val files = mutableListOf<Triple<String, String, String>>()
                scanRecursiveAllFiles(rootDocumentFile, "", files)

                if (files.isEmpty()) {
                    builder.append("❌ Aucun fichier trouvé dans le dossier et ses sous-dossiers.\n")
                } else {
                    builder.append("✓ ${files.size} fichiers trouvés et convertis :\n\n")
                    builder.append("====================================\n\n")

                    for ((relativePath, fileType, content) in files) {
                        builder.append("═══════════════════════════════════════════\n")
                        builder.append("FICHIER: $relativePath\n")
                        builder.append("TYPE: $fileType\n")
                        builder.append("═══════════════════════════════════════════\n")
                        builder.append(content)
                        builder.append("\n\n")
                    }
                }

                builder.append("\n════════════════════════════════════════════\n")
                builder.append("RÉSUMÉ FINAL:\n")
                builder.append("════════════════════════════════════════════\n")
                builder.append("Total fichiers traités: $totalFiles\n")
                builder.append("Fichiers réussis: $successFiles\n")
                builder.append("Fichiers échoués: $failedFiles\n")
                builder.append("Dossiers parcourus: $totalFolders\n")
                builder.append("Fichier final: ${outputFile.absolutePath}\n")
                builder.append("Date/Heure finale: ${Date()}\n")

                outputFile.writeText(builder.toString(), Charsets.UTF_8)

                runOnUiThread {
                    statusTextView.text = "✓ Conversion réussie !\n$successFiles/$totalFiles fichiers traités\n$totalFolders dossiers parcourus"
                    Toast.makeText(this, "Fichier créé: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusTextView.text = "✗ Erreur: ${e.message}"
                    Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun scanRecursiveAllFiles(folder: DocumentFile, currentPath: String, files: MutableList<Triple<String, String, String>>) {
        if (folder.name == null) return

        totalFolders++

        val path = if (currentPath.isEmpty()) folder.name!! else "$currentPath/${folder.name}"

        val children = folder.listFiles()

        for (child in children) {
            if (child.isDirectory) {
                if (!child.name!!.startsWith(".")) {
                    scanRecursiveAllFiles(child, path, files)
                }
            } else {
                if (child.name == null) continue
                if (child.name!!.startsWith(".")) continue

                totalFiles++

                val fileType = getFileType(child.name!!)
                val content = readFileContent(child)

                if (content != null) {
                    files.add(Triple(path + "/" + child.name, fileType, content))
                    successFiles++
                } else {
                    failedFiles++
                }
            }
        }
    }

    private fun getFileType(filename: String): String {
        val extension = filename.substringAfterLast(".", "")
        return if (extension.isNotEmpty()) extension.uppercase() else "INCONNU"
    }

    private fun readFileContent(file: DocumentFile): String? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
            if (inputStream == null) {
                return "[Erreur: Impossible d'ouvrir le fichier]"
            }

            val fileSize = file.length()
            if (fileSize > MAX_FILE_SIZE) {
                return "[Fichier trop volumineux (${fileSize / 1024 / 1024} MB > 10 MB limit) - Contenu tronqué]"
            }

            val content = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                try {
                    reader.readText()
                } catch (e: Exception) {
                    inputStream.close()
                    try {
                        val inputStream2 = contentResolver.openInputStream(file.uri)
                        inputStream2?.bufferedReader(Charset.defaultCharset()).use { it.readText() } ?: "[Impossible de décoder le fichier]"
                    } catch (e2: Exception) {
                        "[Erreur de décodage: ${e.message}]"
                    }
                }
            }

            if (content.isEmpty()) {
                "[Fichier vide]"
            } else {
                content
            }

        } catch (e: Exception) {
            "[Erreur de lecture: ${e.message}]"
        }
    }
}
