package com.example.drawiton

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var mPaintColor : ImageButton? = null
    var imageBackground : ImageView? = null
    var imagePath : String = ""

    var customDialog : Dialog? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode == RESULT_OK &&
                    result.data != null){
                imageBackground = findViewById(R.id.demoImageView)
                imageBackground?.setImageURI(result.data?.data)
            }
        }

    private val resultLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value
                if(isGranted) {
                    Toast.makeText(
                        this,
                        "Permission granted for reading files from external storage.",
                        Toast.LENGTH_SHORT
                    ).show()

                    val pickIntent = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(
                            this,
                            "Permission denied for reading files from external storage.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView!!.setBrushSize(10.toFloat())

        val linearLayout: LinearLayout = findViewById(R.id.ll_color_pallet)
        mPaintColor = linearLayout[1] as ImageButton
        mPaintColor!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val brushDialog : ImageButton = findViewById(R.id.ib_brushDialog)
        brushDialog.setOnClickListener {
            brushSizeChooserDialog()
        }

        val ibImage : ImageButton = findViewById(R.id.ib_image)
        ibImage.setOnClickListener {
            requestForPermission()
        }

        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView!!.onClickUndo()
        }

        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener {
            drawingView!!.onClickRedo()
        }

        val ibSave : ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {

            if(isReadStorageAllowed()){
                showCustomDialogBox()
                lifecycleScope.launch{
                    val flDrawingView : FrameLayout = findViewById(R.id.mainFrameLayout)
                    imagePath = saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }

        val ibNewfile : ImageButton = findViewById(R.id.ib_newfile)
        ibNewfile.setOnClickListener{
            removeBackground()
            imagePath = ""
            drawingView!!.onClickNew()
        }

        val ibShare : ImageButton = findViewById(R.id.ib_share)
        ibShare.setOnClickListener {

            if(imagePath.isNotEmpty()){
                shareImage(imagePath)
            }else{
                val alertDialog : AlertDialog.Builder = AlertDialog.Builder(this)
                alertDialog.setTitle("Draw It On")
                alertDialog.setMessage("Please save the drawing before sharing.")
                alertDialog.setIcon(R.drawable.icon_alert)
                alertDialog.setPositiveButton("Cancel"){dialog, _->
                    dialog.dismiss()
                }
                alertDialog.setCancelable(false)
                alertDialog.create().show()
            }

        }
    }

    private fun removeBackground(){

        if(imageBackground != null)
            imageBackground?.setImageResource(0)
    }

    private fun brushSizeChooserDialog(){
        val brushSize = Dialog(this)
        brushSize.setContentView(R.layout.dialog_brush_size)
        brushSize.setTitle("Brush Size : ")
        val smallBtn:ImageButton = brushSize.findViewById(R.id.small_brush_button)
        smallBtn.setOnClickListener {
            drawingView!!.setBrushSize(5.toFloat())
            brushSize.dismiss()
        }

        val mediumBtn:ImageButton = brushSize.findViewById(R.id.medium_brush_button)
        mediumBtn.setOnClickListener {
            drawingView!!.setBrushSize(15.toFloat())
            brushSize.dismiss()
        }

        val largeBtn:ImageButton = brushSize.findViewById(R.id.large_brush_button)
        largeBtn.setOnClickListener {
            drawingView!!.setBrushSize(25.toFloat())
            brushSize.dismiss()
        }
        brushSize.show()
    }

    fun setPaintPressed(view : View){

        if(view != mPaintColor){

            val imageButton = view as ImageButton
            val btnColor = imageButton.tag.toString()
            drawingView!!.setColor(btnColor)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )

            mPaintColor!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )

            mPaintColor = view
        }
    }

    private fun showRationaleDialog(title:String, message:String){

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)

        builder.setPositiveButton("Cancel"){ dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        builder.setCancelable(false)
        builder.create().show()
    }

    private fun isReadStorageAllowed() : Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestForPermission(){

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
        )){
            showRationaleDialog(
                "Draw It On",
                "Draw It On requires permission for external storage for setting your background image."
            )
        }
        else{
            resultLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun getBitmapFromView(view:View): Bitmap {

        //we are converting the view in bitmap as view cannot be saved
        val returnedBitmap = Bitmap.createBitmap(view.width,
        view.height,Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap : Bitmap?):String{

        var result = ""
        withContext(Dispatchers.IO){

            if(mBitmap != null){

                try{
                   val bytes = ByteArrayOutputStream()
                   mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)

                   val f = File(externalCacheDir?.absoluteFile.toString()+
                   File.separator + "DrawItOn_"+ System.currentTimeMillis())

                   val fo = FileOutputStream(f)
                   fo.write(bytes.toByteArray())
                   fo.close()

                    result = f.absolutePath

                    runOnUiThread{
                        cancelCustomDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully at $result",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else{
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                catch(e : Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showCustomDialogBox(){
        customDialog = Dialog(this)
        customDialog?.setContentView(R.layout.dialog_custom)
        customDialog?.show()
    }

    private fun cancelCustomDialog(){
        if(customDialog != null){
            customDialog?.dismiss()
            customDialog = null
        }
    }

    private fun shareImage(result : String){

        MediaScannerConnection.scanFile(this, arrayOf(result),null){
           path, uri->
           val shareIntent = Intent()
           shareIntent.action = Intent.ACTION_SEND
           shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
           shareIntent.type = "image/png"
           startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }
}