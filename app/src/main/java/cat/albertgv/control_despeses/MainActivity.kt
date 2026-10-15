package cat.albertgv.control_despeses

import android.os.Bundle
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Purple = Color(0xFF7056B8)
private val Pale = Color(0xFFF5F2FC)
private val Ink = Color(0xFF24212D)
private val Red = Color(0xFFC62828)
private val Green = Color(0xFF23845B)

data class Entry(
    val id: Long,
    val isIncome: Boolean,
    val cents: Long,
    val category: String,
    val timestamp: Long
)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Purple, background = Color(0xFFFAF9FC))) {
            ExpenseApp(this)
        } }

    }
}

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("ca", "ES")).format(cents / 100.0)

private fun loadEntries(context: Context): MutableList<Entry> {
    val raw = context.getSharedPreferences("finance", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"
    val arr = JSONArray(raw)
    return MutableList(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        Entry(o.getLong("id"), o.getBoolean("income"), o.getLong("cents"), o.getString("category"), o.getLong("timestamp"))
    }
}

private fun saveEntries(context: Context, entries: List<Entry>) {
    val arr = JSONArray()
    entries.forEach { e ->
        arr.put(JSONObject().put("id", e.id).put("income", e.isIncome).put("cents", e.cents)
            .put("category", e.category).put("timestamp", e.timestamp))
    }
    context.getSharedPreferences("finance", Context.MODE_PRIVATE).edit().putString("entries", arr.toString()).apply()
}


private fun loadCategoryMap(context: Context): Map<String, List<String>> {
    val raw = context.getSharedPreferences("finance", Context.MODE_PRIVATE).getString("category_map", null) ?: return mapOf("Compres" to listOf("Menjar", "Roba", "Higiene", "Llar", "Tecnologia", "Regals", "Altres"), "Online" to listOf("Clash Royale", "Brawl Stars", "Subscripcions", "Compres web"), "Transport" to listOf("Bus", "Tren", "Taxi", "Gasolina"), "Oci" to listOf("Sortides", "Cinema", "Jocs"), "Ingressos" to listOf("Efectiu", "Bizum", "Família", "Sou"))
    return try { val o = JSONObject(raw); o.keys().asSequence().associateWith { k -> val a=o.getJSONArray(k); (0 until a.length()).map { a.getString(it) } } } catch (_: Exception) { emptyMap() }
}
private fun saveCategoryMap(context: Context, values: Map<String, List<String>>) {
    val o=JSONObject(); values.forEach { (k,v)->o.put(k, JSONArray(v)) }
    context.getSharedPreferences("finance", Context.MODE_PRIVATE).edit().putString("category_map", o.toString()).apply()
}


private fun authenticateBank(context: Context, onSuccess: () -> Unit) {
    val activity = context as? FragmentActivity ?: return
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(context).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) return
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Control Despeses").setSubtitle("Confirm the bank operation").setAllowedAuthenticators(allowed).build())
}

@Composable
fun ExpenseApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("finance", Context.MODE_PRIVATE) }
    var entries by remember { mutableStateOf(loadEntries(context).sortedByDescending { it.timestamp }) }
    var budgetText by remember { mutableStateOf(prefs.getInt("budget", 30000).let { (it / 100.0).toString() }) }
    var budgetCents by remember { mutableLongStateOf(prefs.getInt("budget", 30000).toLong()) }
    var initialText by remember { mutableStateOf(prefs.getLong("initial", 0L).let { (it / 100.0).toString() }) }
    var initialCents by remember { mutableLongStateOf(prefs.getLong("initial", 0L)) }
    var showAdd by remember { mutableStateOf(false) }
    var isIncome by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Menjar") }
    var tab by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    var lastDeleted by remember { mutableStateOf<Entry?>(null) }
    var bankBalance by remember { mutableLongStateOf(prefs.getLong("bank", 0L)) }
    var bankAmount by remember { mutableStateOf("") }
    var bankIn by remember { mutableStateOf(true) }
    var challengeName by remember { mutableStateOf("") }
    var challengeTarget by remember { mutableStateOf("") }
    var challenges by remember { mutableStateOf(prefs.getString("challenges", "") ?: "") }
    var categoryMap by remember { mutableStateOf(loadCategoryMap(context)) }
    var newMainCategory by remember { mutableStateOf("") }
    var newSubCategory by remember { mutableStateOf("") }
    var aiQuestion by remember { mutableStateOf("") }
    val categories = categoryMap.flatMap { (parent, children) -> children.map { parent + " › " + it } } + categoryMap.keys
    LaunchedEffect(categoryMap) { saveCategoryMap(context, categoryMap) }
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = now
    val month = cal.get(java.util.Calendar.MONTH)
    val year = cal.get(java.util.Calendar.YEAR)
    fun sameMonth(t: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
        return c.get(java.util.Calendar.MONTH) == month && c.get(java.util.Calendar.YEAR) == year
    }
    val thisMonth = entries.filter { sameMonth(it.timestamp) }
    val spent = thisMonth.filter { !it.isIncome }.sumOf { it.cents }
    val income = thisMonth.filter { it.isIncome }.sumOf { it.cents }
    val balance = initialCents + entries.sumOf { if (it.isIncome) it.cents else -it.cents }
    val remaining = budgetCents - spent

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Inici", "Moviments", "Banc", "Reptes", "Categories", "IA", "Ajustos").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index; settings = index == 2 },
                        icon = { Text(listOf("⌂", "↕", "▣", "★", "☷", "✦", "⚙")[index], fontSize = 18.sp) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 1) FloatingActionButton(onClick = { showAdd = true }, containerColor = Purple, contentColor = Color.White) {
                Text("+", fontSize = 30.sp)
            }
        },
        containerColor = Color(0xFFFAF9FC)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (tab) {
                0 -> {
                    Text("Control Despeses", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("El teu resum financer", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp)) {
                            Text("Saldo real", color = Color.DarkGray)
                            Text(money(balance), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 6.dp))
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Ingressos", color = Color.Gray, fontSize = 12.sp); Text(money(income), color = Green, fontWeight = FontWeight.SemiBold) }
                                Column { Text("Despeses", color = Color.Gray, fontSize = 12.sp); Text(money(spent), color = Red, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                    Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Pressupost d'aquest mes", fontWeight = FontWeight.SemiBold)
                            Text(money(remaining), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (remaining < 0) Red else Purple, modifier = Modifier.padding(top = 6.dp))
                            Text("de ${money(budgetCents)} · les despeses no es bloquegen", color = Color.Gray, fontSize = 12.sp)
                            LinearProgressIndicator(progress = { if (budgetCents <= 0) 1f else (spent.toFloat() / budgetCents).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = if (remaining < 0) Red else Purple)
                        }
                    }
                    Text("Últimes operacions", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                    if (entries.isEmpty()) Text("Encara no tens moviments. Prem + per afegir-ne un.", color = Color.Gray)
                    else LazyColumn { items(entries.take(5), key = { it.id }) { e -> EntryRow(e) } }
                }
                1 -> {
                    Text("Moviments", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    if (lastDeleted != null) {
                        OutlinedButton(onClick = {
                            val restore = lastDeleted
                            if (restore != null && entries.none { it.id == restore.id }) {
                                entries = (entries + restore).sortedByDescending { it.timestamp }
                                saveEntries(context, entries)
                            }
                            lastDeleted = null
                        }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) { Text("Desfer eliminació") }
                    }
                    if (entries.isEmpty()) Text("Encara no hi ha moviments.")
                    else LazyColumn { items(entries, key = { it.id }) { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(42.dp).background(Pale, CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (e.isIncome) "↙" else "↗", color = Purple, fontSize = 20.sp)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                                Text(e.category, fontWeight = FontWeight.SemiBold)
                                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ca", "ES")).format(Date(e.timestamp)), color = Color.Gray, fontSize = 12.sp)
                            }
                            Text((if (e.isIncome) "+" else "−") + money(e.cents), color = if (e.isIncome) Green else Red, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { pendingDelete = e }) { Text("Desfer", color = Red) }
                        }
                        HorizontalDivider(color = Color(0xFFEDEAF2))
                    } }
                }
                2 -> {
                    Text("El meu banc", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Saldo bancari intern: " + money(prefs.getLong("bank", 0L)), fontSize = 26.sp, modifier = Modifier.padding(vertical = 12.dp))
                    OutlinedTextField(value = bankAmount, onValueChange = { bankAmount = it }, label = { Text("Import (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Row { Button(onClick = { authenticateBank(context) { val v = bankAmount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100).toLong() }; if (v != null) { bankBalance += v; prefs.edit().putLong("bank", bankBalance).apply(); bankAmount = "" } } }) { Text("Ingressar") }; Spacer(Modifier.width(8.dp)); Button(onClick = { authenticateBank(context) { val v = bankAmount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100).toLong() }; if (v != null && bankBalance >= v) { bankBalance -= v; prefs.edit().putLong("bank", bankBalance).apply(); bankAmount = "" } } }) { Text("Retirar") } }
                    Text("Banc intern de prova, no és un compte bancari real.", color=Color.Gray, fontSize=12.sp)
                }
                3 -> {
                    Text("Reptes d'estalvi", fontSize=24.sp, fontWeight=FontWeight.Bold)
                    OutlinedTextField(value=challengeName,onValueChange={challengeName=it},label={Text("Nom del repte")},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                    OutlinedTextField(value=challengeTarget,onValueChange={challengeTarget=it},label={Text("Objectiu (€)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                    Button(onClick={val v=challengeTarget.replace(',', '.').toDoubleOrNull()?.takeIf{it>0}?.let{(it*100).toLong()};if(challengeName.isNotBlank()&&v!=null){challenges=(challenges.split("\n").filter{it.isNotBlank()}+(challengeName.trim()+"|"+v)).joinToString("\n");prefs.edit().putString("challenges",challenges).apply();challengeName="";challengeTarget=""}},modifier=Modifier.padding(top=8.dp)){Text("Crear repte")}
                    challenges.split("\n").filter{it.isNotBlank()}.forEach{Text("★ "+it.replace("|"," · "),modifier=Modifier.padding(top=8.dp))}
                }
                4 -> {
                    Text("Categories i subcategories",fontSize=22.sp,fontWeight=FontWeight.Bold)
                    OutlinedTextField(value=newMainCategory,onValueChange={newMainCategory=it},label={Text("Nova categoria")},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                    Button(onClick={if(newMainCategory.isNotBlank()&&!categoryMap.containsKey(newMainCategory.trim())){categoryMap=categoryMap+(newMainCategory.trim() to listOf("Altres"));newMainCategory=""}},modifier=Modifier.padding(top=4.dp)){Text("Crear categoria")}
                    categoryMap.forEach{(parent,children)->var expanded by remember(parent){mutableStateOf(false)};Card(Modifier.fillMaxWidth().padding(top=6.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(10.dp)){Text(parent,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{expanded=!expanded});if(expanded){children.forEach{Text("• "+it)};OutlinedTextField(value=newSubCategory,onValueChange={newSubCategory=it},label={Text("Nova subcategoria")},modifier=Modifier.fillMaxWidth());TextButton(onClick={if(newSubCategory.isNotBlank()){categoryMap=categoryMap+(parent to (children+newSubCategory.trim()).distinct());newSubCategory=""}}){Text("Afegir subcategoria")}}}}}
                    Text("Compres: Menjar, Roba, Higiene, Llar, Tecnologia, Regals i Altres.",fontSize=12.sp,color=Color.Gray)
                }
                5 -> {
                    Text("Assistent financer",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("Aquest mes has gastat "+money(spent)+" i et queden "+money(remaining)+" del pressupost.",modifier=Modifier.padding(top=10.dp))
                    val top=thisMonth.filter{!it.isIncome}.groupBy{it.category}.mapValues{it.value.sumOf{e->e.cents}}.maxByOrNull{it.value}
                    if(top!=null)Text("Categoria amb més despesa: "+top.key+" ("+money(top.value)+").",modifier=Modifier.padding(top=8.dp))
                    OutlinedTextField(value=aiQuestion,onValueChange={aiQuestion=it},label={Text("Pregunta sobre les teves despeses")},modifier=Modifier.fillMaxWidth().padding(top=10.dp))
                    Text(if(aiQuestion.isBlank())"Escriu una pregunta." else "Consell bàsic: revisa la categoria més alta i compara-la amb el pressupost.",modifier=Modifier.padding(top=8.dp))
                    Text("Això és un resum local, no una IA generativa real. Per connectar-ne una cal configurar un servei i una clau API.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=10.dp))
                }
                else -> {
                    Text("Configuració", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Configuració local inicial", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                    OutlinedTextField(value = budgetText, onValueChange = { budgetText = it }, label = { Text("Pressupost mensual (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = initialText, onValueChange = { initialText = it }, label = { Text("Saldo inicial (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    Button(onClick = {
                        budgetText.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { budgetCents = (it * 100).toLong(); prefs.edit().putInt("budget", budgetCents.toInt()).apply() }
                        initialText.replace(',', '.').toDoubleOrNull()?.let { initialCents = (it * 100).toLong(); prefs.edit().putLong("initial", initialCents).apply() }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Desar configuració") }
                    Text("Aquesta primera versió desa les dades al dispositiu. Google, núvol, IA i biometria encara s'han d'integrar.", color = Color.Gray, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Control Despeses ·", fontSize = 10.sp, color = Color.Gray)
                Text("Fet per agviu 🖤🤍", fontSize = 10.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            }
    }

    if (pendingDelete != null) {
        val target = pendingDelete
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Desfer aquest moviment?") },
            text = { Text("S'eliminarà ${target?.category} (${target?.let { money(it.cents) }}). El saldo i el pressupost es recalcularan.") },
            confirmButton = {
                TextButton(onClick = {
                    if (target != null) {
                        entries = entries.filterNot { it.id == target.id }
                        saveEntries(context, entries)
                        lastDeleted = target
                    }
                    pendingDelete = null
                }) { Text("Sí, desfer") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel·lar") } }
        )
    }

    if (showAdd) {
        var localAmount by remember { mutableStateOf(amountText) }
        var localCategory by remember { mutableStateOf(category) }
        var localIncome by remember { mutableStateOf(isIncome) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Afegir moviment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !localIncome, onClick = { localIncome = false }, label = { Text("Despesa") })
                        FilterChip(selected = localIncome, onClick = { localIncome = true }, label = { Text("Ingrés") })
                    }
                    OutlinedTextField(value = localAmount, onValueChange = { localAmount = it }, label = { Text("Import (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Text("Categoria", fontWeight = FontWeight.SemiBold)
                    Column(Modifier.heightIn(max = 190.dp)) {
                        LazyColumn { items(categories) { c ->
                            Row(Modifier.fillMaxWidth().clickable { localCategory = c }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = localCategory == c, onClick = { localCategory = c })
                                Text(c)
                            }
                        } }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val cents = localAmount.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toLong() }
                if (cents != null && cents > 0) {
                    val e = Entry(System.currentTimeMillis(), localIncome, cents, localCategory, System.currentTimeMillis())
                    entries = (listOf(e) + entries).sortedByDescending { it.timestamp }
                    saveEntries(context, entries)
                    showAdd = false
                }
            }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel·lar") } }
        )
    }
}

@Composable
private fun EntryRow(e: Entry) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.size(40.dp).background(Pale, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (e.isIncome) "↓" else "↑", color = Purple, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.category, fontWeight = FontWeight.SemiBold)
            Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ca", "ES")).format(Date(e.timestamp)), color = Color.Gray, fontSize = 12.sp)
        }
        Text((if (e.isIncome) "+" else "−") + money(e.cents), color = if (e.isIncome) Green else Red, fontWeight = FontWeight.Bold)
    }
}
