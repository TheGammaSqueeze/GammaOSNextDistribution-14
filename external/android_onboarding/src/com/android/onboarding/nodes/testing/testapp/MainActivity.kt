package com.android.onboarding.nodes.testing.testapp

import android.content.Context
import android.content.Intent
import android.content.Intent.CATEGORY_DEFAULT
import android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT
import android.content.pm.PackageManager.ResolveInfoFlags
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.android.onboarding.common.TEST_APP
import com.android.onboarding.contracts.ContractResult
import com.android.onboarding.contracts.IdentifyExecutingContract
import com.android.onboarding.contracts.OnboardingActivityApiContract
import com.android.onboarding.contracts.annotations.OnboardingNode
import com.android.onboarding.contracts.failNode
import com.android.onboarding.contracts.findExecutingContract
import com.android.onboarding.contracts.registerForActivityLaunch

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    val contracts = arrayOf(RedContract(), BlueContract(), GreenContract())
    val contract = intent?.findExecutingContract(*contracts) ?: RedContract()

    if (contract.attach(this, intent).validated) {
      findViewById<TextView>(R.id.status).text =
        "Received argument: ${contract.extractArgument(intent).arg}"
    }

    val processSpinner = findViewById<Spinner>(R.id.processSpinner)
    val packages =
      packageManager
        .queryIntentActivities(
          Intent("com.android.onboarding.nodes.testing.testapp.red"),
          ResolveInfoFlags.of(0),
        )
        .map { it.activityInfo.packageName }
        .toSet()
        .toList()
    val packageNames =
      packages
        .map { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)) }
        .toList()
    processSpinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, packageNames)
    processSpinner.setSelection(packages.indexOf(packageName))

    val contractSpinner = findViewById<Spinner>(R.id.contractSpinner)
    contractSpinner.adapter =
      ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        contracts.map { it.javaClass.getAnnotation(OnboardingNode::class.java)?.name ?: "Unknown" },
      )
    contractSpinner.setSelection(contracts.indexOf(contract))

    val noResultLaunchers = contracts.map { registerForActivityLaunch(it) }
    val resultLaunchers = contracts.map { registerForActivityResult(it, this::onResult) }

    findViewById<TextView>(R.id.packageName).text =
      packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))

    findViewById<LinearLayout>(R.id.bg).setBackgroundColor(contract.colour)

    when (contract) {
      is RedContract -> {
        findViewById<LinearLayout>(R.id.bg).setBackgroundColor(Color.RED)
      }
      is BlueContract -> {
        findViewById<LinearLayout>(R.id.bg).setBackgroundColor(Color.BLUE)
      }
      is GreenContract -> {
        findViewById<LinearLayout>(R.id.bg).setBackgroundColor(Color.GREEN)
      }
    }

    findViewById<Button>(R.id.startActivityAndFinishButton).setOnClickListener {
      val arg = findViewById<EditText>(R.id.argument).text.toString()
      val contractId = findViewById<Spinner>(R.id.contractSpinner).selectedItemId.toInt()
      val contract = noResultLaunchers[contractId]
      val targetPackageName = packages[processSpinner.selectedItemPosition]!!.toString()

      contract.launch(ContractArg(arg, targetPackageName))
      finish()
    }

    findViewById<Button>(R.id.startActivityAndForwardButton).setOnClickListener {
      val arg = findViewById<EditText>(R.id.argument).text.toString()
      val contractId = findViewById<Spinner>(R.id.contractSpinner).selectedItemId.toInt()
      val contract = noResultLaunchers[contractId]
      val targetPackageName = packages[processSpinner.selectedItemPosition]!!.toString()

      contract.launch(ContractArg(arg, targetPackageName, shouldForward = true))
      finish()
    }

    findViewById<Button>(R.id.startActivityButton).setOnClickListener {
      val arg = findViewById<EditText>(R.id.argument).text.toString()
      val contractId = findViewById<Spinner>(R.id.contractSpinner).selectedItemId.toInt()
      val contract = noResultLaunchers[contractId]
      val targetPackageName = packages[processSpinner.selectedItemPosition]!!.toString()

      contract.launch(ContractArg(arg, targetPackageName))
    }

    findViewById<Button>(R.id.startActivityForResultButton).setOnClickListener {
      val arg = findViewById<EditText>(R.id.argument).text.toString()
      val contractId = findViewById<Spinner>(R.id.contractSpinner).selectedItemId.toInt()
      val contract = resultLaunchers[contractId]
      val targetPackageName = packages[processSpinner.selectedItemPosition]!!.toString()

      contract.launch(ContractArg(arg, targetPackageName))
    }

    findViewById<Button>(R.id.finishButton).setOnClickListener { finish() }
    findViewById<Button>(R.id.crashButton).setOnClickListener { null!! }
    findViewById<Button>(R.id.failNodeButton).setOnClickListener {
      failNode(findViewById<EditText>(R.id.argument).text.toString())
    }
    findViewById<Button>(R.id.setResultAndFinishButton).setOnClickListener {
      contract.setResult(this, findViewById<EditText>(R.id.argument).text.toString())
      finish()
    }
  }

  fun onResult(result: String) {
    findViewById<TextView>(R.id.status).text = "Received result: $result"
  }
}

data class ContractArg(
  val arg: String,
  val targetPackageName: String,
  val shouldForward: Boolean = false,
)

abstract class ColourContract(val colour: Int, private val name: String) :
  OnboardingActivityApiContract<ContractArg, String>(), IdentifyExecutingContract {
  override fun isExecuting(intent: Intent) = intent.action?.endsWith(".$name") ?: false

  override fun performCreateIntent(context: Context, arg: ContractArg): Intent =
    Intent("com.android.onboarding.nodes.testing.testapp.$name").apply {
      setPackage(arg.targetPackageName)
      putExtra("KEY", arg.arg)
      addCategory(CATEGORY_DEFAULT)

      if (arg.shouldForward) {
        addFlags(FLAG_ACTIVITY_FORWARD_RESULT)
      }
    }

  override fun performExtractArgument(intent: Intent): ContractArg =
    ContractArg(
      intent.getStringExtra("KEY")!!,
      intent.`package` ?: "",
      intent.hasFlag(FLAG_ACTIVITY_FORWARD_RESULT),
    )

  override fun performParseResult(result: ContractResult): String =
    result.intent?.getStringExtra("KEY")!!

  override fun performSetResult(result: String): ContractResult =
    ContractResult.Success(1, Intent().apply { putExtra("KEY", result) })
}

@OnboardingNode(component = TEST_APP, name = "Red", uiType = OnboardingNode.UiType.OTHER)
class RedContract : ColourContract(Color.RED, "red")

@OnboardingNode(component = TEST_APP, name = "Blue", uiType = OnboardingNode.UiType.OTHER)
class BlueContract : ColourContract(Color.BLUE, "blue")

@OnboardingNode(component = TEST_APP, name = "Green", uiType = OnboardingNode.UiType.OTHER)
class GreenContract : ColourContract(Color.GREEN, "green")

fun Intent.hasFlag(flag: Int) = (this.flags and flag) == flag
