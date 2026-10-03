package com.example.foldertotxt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var selectButton: Button
    private lateinit var convertButton: Button

    private var selectedFolderUri: Uri? = null
    private var fileCount = 0
    private var folderCount = 0

    companion object {
        private const val PERMISSION_REQUEST = 100
        private const val FOLDER_PICKER_REQUEST = 101
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
        if (requestCode == PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFolderPicker()
        } else {
            Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FOLDER_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                selectedFolderUri = uri
                val folderName = uri.lastPathSegment?.split(":")?.last() ?: "Dossier sélectionné"
                statusTextView.text = "Dossier sélectionné: $folderName"
                convertButton.isEnabled = true
            }
        }
    }

    private fun convertFolderToTxt() {
        fileCount = 0
        folderCount = 0
        
        Thread {
            try {
                statusTextView.text = "Conversion en cours..."

                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "FolderToTxt"
                )
                outputDir.mkdirs()

                val outputFile = File(
                    outputDir,
                    "convert_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
                )

                val builder = StringBuilder()
                builder.append("Conversion du dossier en TXT - Tous les fichiers (sous-dossiers inclus)\n")
                builder.append("Date: ${Date()}\n")
                builder.append("===================================\n\n")

                val treeUri = selectedFolderUri ?: return@Thread
                val documentId = DocumentsContract.getTreeDocumentId(treeUri)

                // Parcourir récursivement tous les dossiers
                scanFolderRecursive(treeUri, documentId, "", builder)

                builder.append("\n\n===================================\n")
                builder.append("Résumé de la conversion:\n")
                builder.append("Fichiers trouvés: $fileCount\n")
                builder.append("Dossiers parcourus: $folderCount\n")
                builder.append("Date/Heure: ${Date()}\n")

                outputFile.writeText(builder.toString())

                runOnUiThread {
                    statusTextView.text = "✓ Conversion terminée!\n$fileCount fichiers convertis\n$folderCount dossiers parcourus"
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

    private fun scanFolderRecursive(treeUri: Uri, parentDocId: String, path: String, builder: StringBuilder) {
        folderCount++
        
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val docId = it.getString(0)
                val fileName = it.getString(1)
                val mime = it.getString(2)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                val currentPath = if (path.isEmpty()) fileName else "$path/$fileName"

                // Si c'est un dossier, parcourir récursivement
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    builder.append("\n[DOSSIER] $currentPath\n")
                    builder.append("---\n")
                    scanFolderRecursive(treeUri, docId, currentPath, builder)
                } else {
                    // Si c'est un fichier texte
                    if (mime != null && (mime.startsWith("text/") || mime == "application/octet-stream")) {
                        fileCount++
                        builder.append("\n[FICHIER] $currentPath\n")
                        builder.append("-----------------------------\n")

                        try {
                            contentResolver.openInputStream(childUri)?.use { input ->
                                val content = input.bufferedReader().readText()
                                builder.append(content)
                            }
                        } catch (e: Exception) {
                            builder.append("[Erreur de lecture: ${e.message}]")
                        }

                        builder.append("\n")
                    }
                }
            }
        }
    }
}
