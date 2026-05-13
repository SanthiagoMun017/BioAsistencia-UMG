<?php
// ============================================================
// BioAsistencia - API REST - Proyecto universitario
// ============================================================
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Sensor-Token, Accept');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(200); exit; }

function db(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $pdo = new PDO(
            "mysql:host=".(getenv('DB_HOST')?:'db').";port=".(getenv('DB_PORT')?:'3306').";dbname=".(getenv('DB_DATABASE')?:'bioasistencia').";charset=utf8mb4",
            getenv('DB_USERNAME')?:'biouser',
            getenv('DB_PASSWORD')?:'bio1234',
            [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]
        );
    }
    return $pdo;
}
function ok($data, int $code=200): void { http_response_code($code); echo json_encode(['success'=>true,'message'=>'OK','data'=>$data]); exit; }
function err(string $msg, int $code=400): void { http_response_code($code); echo json_encode(['success'=>false,'message'=>$msg,'data'=>null]); exit; }
function body(): array { return json_decode(file_get_contents('php://input'),true)??[]; }
function auth(): array {
    $h = $_SERVER['HTTP_AUTHORIZATION']??'';
    if (!str_starts_with($h,'Bearer ')) err('No autorizado',401);
    $s = db()->prepare('SELECT u.* FROM sesiones_jwt s JOIN usuarios u ON u.id_usuario=s.id_usuario WHERE s.token_hash=? AND s.expires_at>NOW()');
    $s->execute([hash('sha256',substr($h,7))]);
    $u = $s->fetch(); if (!$u) err('Token invalido',401); return $u;
}
function sensorAuth(): void {
    $t = $_SERVER['HTTP_X_SENSOR_TOKEN']??'';
    $s = db()->prepare('SELECT id_sensor FROM sensores WHERE token_dispositivo=? AND estado="activo"');
    $s->execute([$t]); if (!$s->fetch()) err('Token sensor invalido',401);
}

$method   = $_SERVER['REQUEST_METHOD'];
$uri      = rtrim(preg_replace('#^/api/v1#','',parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH)),'/');
$seg      = array_values(array_filter(explode('/',ltrim($uri,'/'))));

try {

// ── AUTH ──────────────────────────────────────────────────────
if ($uri==='/auth/login' && $method==='POST') {
    $b=body(); $c=trim($b['correo']??''); $p=trim($b['contrasena']??'');
    if (!$c||!$p) err('Correo y contrasena requeridos');
    $s=db()->prepare('SELECT * FROM usuarios WHERE correo=? AND activo=1'); $s->execute([$c]); $u=$s->fetch();
    if (!$u||!password_verify($p,$u['contrasena'])) err('Correo o contrasena incorrectos',401);
    $tok=bin2hex(random_bytes(32)); $hash=hash('sha256',$tok); $exp=date('Y-m-d H:i:s',strtotime('+8 hours'));
    db()->prepare('INSERT INTO sesiones_jwt (id_usuario,token_hash,expires_at) VALUES (?,?,?)')->execute([$u['id_usuario'],$hash,$exp]);
    http_response_code(200);
    echo json_encode(['token'=>$tok,'usuario'=>['id_usuario'=>$u['id_usuario'],'nombre'=>$u['nombre'],'apellido'=>$u['apellido'],'correo'=>$u['correo'],'rol'=>$u['rol']],'message'=>'Login exitoso']);
    exit;
}
if ($uri==='/auth/logout' && $method==='POST') {
    $h=$_SERVER['HTTP_AUTHORIZATION']??'';
    if (str_starts_with($h,'Bearer ')) db()->prepare('DELETE FROM sesiones_jwt WHERE token_hash=?')->execute([hash('sha256',substr($h,7))]);
    ok(null);
}

// ── DASHBOARD ─────────────────────────────────────────────────
if ($uri==='/dashboard' && $method==='GET') {
    auth();
    $s=db()->prepare("SELECT d.id_departamento,d.nombre,d.codigo,COUNT(DISTINCT c.id_catedratico) AS total,SUM(CASE WHEN a.estado='presente' THEN 1 ELSE 0 END) AS presentes,SUM(CASE WHEN a.estado='ausente' THEN 1 ELSE 0 END) AS ausentes,SUM(CASE WHEN a.estado='tardanza' THEN 1 ELSE 0 END) AS tardanzas FROM departamentos d LEFT JOIN catedraticos c ON c.id_departamento=d.id_departamento AND c.activo=1 LEFT JOIN asistencias a ON a.id_catedratico=c.id_catedratico AND a.fecha=? WHERE d.activo=1 GROUP BY d.id_departamento ORDER BY d.nombre");
    $s->execute([date('Y-m-d')]); ok($s->fetchAll());
}

// ── DEPARTAMENTOS ─────────────────────────────────────────────
if ($uri==='/departamentos' && $method==='GET') {
    auth(); ok(db()->query('SELECT * FROM departamentos WHERE activo=1 ORDER BY nombre')->fetchAll());
}

// ── CATEDRATICOS ─────────────────────────────────────────────
if ($uri==='/catedraticos' && $method==='GET') {
    auth();
    $sql="SELECT c.*,d.nombre AS departamento,(SELECT COUNT(*) FROM huellas_digitales h WHERE h.id_catedratico=c.id_catedratico AND h.activa=1)>0 AS huella_registrada,(SELECT h.id_huella FROM huellas_digitales h WHERE h.id_catedratico=c.id_catedratico AND h.activa=1 LIMIT 1) AS id_huella FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.activo=1";
    $p=[];
    if ($_GET['id_departamento']??null) { $sql.=' AND c.id_departamento=?'; $p[]=$_GET['id_departamento']; }
    if ($_GET['buscar']??null) { $sql.=' AND (c.nombre LIKE ? OR c.apellido LIKE ? OR c.codigo LIKE ?)'; $l="%{$_GET['buscar']}%"; $p=array_merge($p,[$l,$l,$l]); }
    $sql.=' ORDER BY c.apellido,c.nombre';
    $s=db()->prepare($sql); $s->execute($p); ok($s->fetchAll());
}

// GET /catedraticos/{id}
if (count($seg)===2 && $seg[0]==='catedraticos' && $method==='GET') {
    auth(); $id=(int)$seg[1];
    $s=db()->prepare("SELECT c.*,d.nombre AS departamento,(SELECT COUNT(*) FROM huellas_digitales h WHERE h.id_catedratico=c.id_catedratico AND h.activa=1)>0 AS huella_registrada,(SELECT h.id_huella FROM huellas_digitales h WHERE h.id_catedratico=c.id_catedratico AND h.activa=1 LIMIT 1) AS id_huella,ROUND((SELECT COUNT(*) FROM asistencias a WHERE a.id_catedratico=c.id_catedratico AND a.estado IN('presente','tardanza'))/GREATEST((SELECT COUNT(*) FROM asistencias a WHERE a.id_catedratico=c.id_catedratico),1)*100,0) AS porcentaje_asistencia,(SELECT COUNT(*) FROM asistencias a WHERE a.id_catedratico=c.id_catedratico AND a.estado IN('presente','tardanza')) AS dias_asistidos,(SELECT COUNT(*) FROM asistencias a WHERE a.id_catedratico=c.id_catedratico AND a.estado='ausente') AS total_ausencias FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?");
    $s->execute([$id]); $r=$s->fetch(); if (!$r) err('No encontrado',404); ok($r);
}

// POST /catedraticos — crear desde la app
if ($uri==='/catedraticos' && $method==='POST') {
    auth(); $b=body();
    $nom=trim($b['nombre']??''); $ape=trim($b['apellido']??''); $dept=(int)($b['id_departamento']??0);
    if (!$nom||!$ape||!$dept) err('nombre, apellido e id_departamento son requeridos');
    $ultimo=db()->query("SELECT codigo FROM catedraticos ORDER BY id_catedratico DESC LIMIT 1")->fetchColumn();
    $num=$ultimo?((int)substr($ultimo,4)+1):1;
    $codigo='CAT-'.str_pad($num,3,'0',STR_PAD_LEFT);
    db()->prepare('INSERT INTO catedraticos (codigo,nombre,apellido,correo,id_departamento,tipo_contrato,horario,turno,cursos_asignados) VALUES (?,?,?,?,?,?,?,?,?)')
        ->execute([$codigo,$nom,$ape,($b['correo']??null)?:null,$dept,$b['tipo_contrato']??'tiempo_completo',$b['horario']??null,$b['turno']??'matutino',(int)($b['cursos_asignados']??0)]);
    $newId=db()->lastInsertId();
    $s=db()->prepare('SELECT c.*,d.nombre AS departamento,false AS huella_registrada,null AS id_huella FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?');
    $s->execute([$newId]); ok($s->fetch(),201);
}

// PUT /catedraticos/{id}
if (count($seg)===2 && $seg[0]==='catedraticos' && $method==='PUT') {
    auth(); $id=(int)$seg[1]; $b=body(); $fields=[]; $params=[];
    foreach (['nombre','apellido','correo','tipo_contrato','horario','turno'] as $f) { if (isset($b[$f])) { $fields[]="$f=?"; $params[]=$b[$f]; } }
    if (isset($b['id_departamento']))  { $fields[]='id_departamento=?';  $params[]=(int)$b['id_departamento']; }
    if (isset($b['cursos_asignados'])) { $fields[]='cursos_asignados=?'; $params[]=(int)$b['cursos_asignados']; }
    if (empty($fields)) err('No hay campos');
    $params[]=$id;
    db()->prepare('UPDATE catedraticos SET '.implode(',',$fields).' WHERE id_catedratico=?')->execute($params);
    $s=db()->prepare('SELECT c.*,d.nombre AS departamento FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?');
    $s->execute([$id]); ok($s->fetch());
}

// DELETE /catedraticos/{id} — desactivar catedratico
if (count($seg)===2 && $seg[0]==='catedraticos' && $method==='DELETE') {
    auth(); $id=(int)$seg[1];
    db()->prepare('UPDATE catedraticos SET activo=0 WHERE id_catedratico=?')->execute([$id]);
    ok(null);
}

// ── ASISTENCIAS ───────────────────────────────────────────────
if ($uri==='/asistencias' && $method==='GET') {
    auth();
    $fecha=$_GET['fecha']??date('Y-m-d'); $dept=$_GET['id_departamento']??null; $est=$_GET['estado']??null; $bus=$_GET['buscar']??null;
    $sql="SELECT a.*,c.codigo,c.nombre,c.apellido,c.tipo_contrato,c.horario,c.turno,c.cursos_asignados,d.nombre AS departamento,c.id_departamento FROM asistencias a JOIN catedraticos c ON c.id_catedratico=a.id_catedratico JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE a.fecha=?";
    $p=[$fecha];
    if ($dept){$sql.=' AND c.id_departamento=?';$p[]=$dept;}
    if ($est) {$sql.=' AND a.estado=?';$p[]=$est;}
    if ($bus) {$sql.=' AND (c.nombre LIKE ? OR c.apellido LIKE ? OR c.codigo LIKE ?)';$l="%$bus%";$p=array_merge($p,[$l,$l,$l]);}
    $sql.=' ORDER BY a.fecha_hora DESC';
    $s=db()->prepare($sql); $s->execute($p);
    $result=array_map(function($r){return ['id_asistencia'=>$r['id_asistencia'],'id_catedratico'=>$r['id_catedratico'],'id_sensor'=>$r['id_sensor'],'fecha_hora'=>$r['fecha_hora'],'estado'=>$r['estado'],'metodo_registro'=>$r['metodo_registro'],'observacion'=>$r['observacion'],'catedratico'=>['id_catedratico'=>$r['id_catedratico'],'codigo'=>$r['codigo'],'nombre'=>$r['nombre'],'apellido'=>$r['apellido'],'tipo_contrato'=>$r['tipo_contrato'],'horario'=>$r['horario'],'turno'=>$r['turno'],'cursos_asignados'=>$r['cursos_asignados'],'departamento'=>$r['departamento'],'id_departamento'=>$r['id_departamento'],'correo'=>null]];}, $s->fetchAll());
    ok($result);
}
if (count($seg)===3&&$seg[0]==='asistencias'&&$seg[1]==='catedratico'&&$method==='GET') {
    auth(); $id=(int)$seg[2];
    $s=db()->prepare('SELECT * FROM asistencias WHERE id_catedratico=? ORDER BY fecha_hora DESC LIMIT 100');
    $s->execute([$id]); ok($s->fetchAll());
}
if ($uri==='/asistencias' && $method==='POST') {
    auth(); $b=body(); $idCat=(int)($b['id_catedratico']??0);
    if (!$idCat) err('id_catedratico requerido');
    $dup=db()->prepare('SELECT id_asistencia FROM asistencias WHERE id_catedratico=? AND fecha=CURDATE()'); $dup->execute([$idCat]);
    if ($dup->fetch()) err('Ya tiene asistencia hoy',409);
    db()->prepare('INSERT INTO asistencias (id_catedratico,id_sensor,estado,metodo_registro,observacion,fecha_hora) VALUES (?,?,?,?,?,NOW())')->execute([$idCat,$b['id_sensor']??null,$b['estado']??'presente',$b['metodo_registro']??'biometrico',$b['observacion']??null]);
    $nId=db()->lastInsertId();
    $a=db()->prepare('SELECT * FROM asistencias WHERE id_asistencia=?'); $a->execute([$nId]);
    $c=db()->prepare('SELECT c.*,d.nombre AS departamento FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?'); $c->execute([$idCat]);
    http_response_code(201); echo json_encode(['message'=>'Asistencia registrada','asistencia'=>$a->fetch(),'catedratico'=>$c->fetch()]); exit;
}
if ($uri==='/asistencias/manual' && $method==='POST') {
    auth(); $b=body(); $idCat=(int)($b['id_catedratico']??0);
    if (!$idCat) err('id_catedratico requerido');
    $dup=db()->prepare('SELECT id_asistencia FROM asistencias WHERE id_catedratico=? AND fecha=CURDATE()'); $dup->execute([$idCat]);
    if ($dup->fetch()) err('Ya tiene asistencia hoy',409);
    db()->prepare('INSERT INTO asistencias (id_catedratico,estado,metodo_registro,observacion,fecha_hora) VALUES (?,?,?,?,NOW())')->execute([$idCat,$b['estado']??'presente','manual',$b['observacion']??'Registro manual']);
    $nId=db()->lastInsertId();
    $a=db()->prepare('SELECT * FROM asistencias WHERE id_asistencia=?'); $a->execute([$nId]);
    $c=db()->prepare('SELECT c.*,d.nombre AS departamento FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?'); $c->execute([$idCat]);
    http_response_code(201); echo json_encode(['message'=>'Asistencia manual registrada','asistencia'=>$a->fetch(),'catedratico'=>$c->fetch()]); exit;
}

// ── SENSOR ────────────────────────────────────────────────────
if ($uri==='/sensor/dato' && $method==='POST') {
    sensorAuth(); $b=body(); $idS=(int)($b['id_sensor']??1); $slot=(int)($b['sensor_slot']??0); $ts=(int)($b['timestamp']??time());
    if (!$slot) err('sensor_slot requerido');
    $s=db()->prepare('SELECT id_catedratico FROM huellas_digitales WHERE sensor_slot=? AND activa=1 LIMIT 1'); $s->execute([$slot]); $h=$s->fetch(); $idCat=$h?$h['id_catedratico']:null;
    db()->prepare('INSERT INTO sensor_eventos (id_sensor,sensor_slot,id_catedratico,timestamp_sensor,procesado) VALUES (?,?,?,?,0)')->execute([$idS,$slot,$idCat,$ts]);
    db()->prepare('UPDATE sensores SET ultima_conexion=NOW() WHERE id_sensor=?')->execute([$idS]);
    if (!$idCat) err('Huella no registrada',404);
    $dup=db()->prepare('SELECT id_asistencia FROM asistencias WHERE id_catedratico=? AND fecha=CURDATE()'); $dup->execute([$idCat]);
    if (!$dup->fetch()) db()->prepare('INSERT INTO asistencias (id_catedratico,id_sensor,estado,metodo_registro,fecha_hora) VALUES (?,?,?,?,NOW())')->execute([$idCat,$idS,'presente','biometrico']);
    db()->prepare('UPDATE sensor_eventos SET procesado=1 WHERE id_sensor=? AND sensor_slot=? ORDER BY created_at DESC LIMIT 1')->execute([$idS,$slot]);
    $c=db()->prepare('SELECT c.*,d.nombre AS departamento FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?'); $c->execute([$idCat]);
    http_response_code(201); echo json_encode(['estado'=>'presente','catedratico'=>$c->fetch()]); exit;
}
if ($uri==='/sensor/ultimo-evento' && $method==='GET') {
    auth(); $idS=(int)($_GET['id_sensor']??1);
    $s=db()->prepare('SELECT * FROM sensor_eventos WHERE id_sensor=? AND procesado=0 ORDER BY created_at DESC LIMIT 1'); $s->execute([$idS]); ok($s->fetch()?:null);
}

// POST /sensor/iniciar-registro — app le pide al sensor que registre la huella de un catedratico
if ($uri==='/sensor/iniciar-registro' && $method==='POST') {
    auth(); $b=body(); $idCat=(int)($b['id_catedratico']??0); $idSens=(int)($b['id_sensor']??1);
    if (!$idCat) err('id_catedratico requerido');
    // Cancelar solicitudes anteriores pendientes
    db()->prepare('UPDATE sensor_eventos SET procesado=2 WHERE id_sensor=? AND procesado=-1')->execute([$idSens]);
    // Siguiente slot libre
    $s=db()->prepare('SELECT MAX(sensor_slot) AS ultimo FROM huellas_digitales WHERE id_sensor=?'); $s->execute([$idSens]); $r=$s->fetch();
    $slot=($r['ultimo']??0)+1; if ($slot>127) err('Sin slots disponibles');
    // Insertar solicitud con procesado=-1 (pendiente de registro)
    db()->prepare('INSERT INTO sensor_eventos (id_sensor,sensor_slot,id_catedratico,timestamp_sensor,procesado) VALUES (?,?,?,?,-1)')->execute([$idSens,$slot,$idCat,time()]);
    $newId=db()->lastInsertId();
    ok(['id_evento'=>$newId,'slot_asignado'=>$slot,'id_catedratico'=>$idCat,'mensaje'=>'Solicitud enviada. Coloca el dedo en el sensor.']);
}

// GET /sensor/pendiente-registro — el ESP32 consulta si hay solicitud pendiente
if ($uri==='/sensor/pendiente-registro' && $method==='GET') {
    sensorAuth(); $idS=(int)($_GET['id_sensor']??1);
    $s=db()->prepare('SELECT * FROM sensor_eventos WHERE id_sensor=? AND procesado=-1 ORDER BY created_at ASC LIMIT 1'); $s->execute([$idS]); ok($s->fetch()?:null);
}

// POST /sensor/confirmar-registro — el ESP32 confirma que ya grabo la huella
if ($uri==='/sensor/confirmar-registro' && $method==='POST') {
    sensorAuth(); $b=body(); $idEvt=(int)($b['id_evento']??0); $slot=(int)($b['sensor_slot']??0); $idCat=(int)($b['id_catedratico']??0); $idS=(int)($b['id_sensor']??1);
    if (!$slot||!$idCat) err('sensor_slot e id_catedratico requeridos');
    db()->prepare('UPDATE sensor_eventos SET procesado=1 WHERE id_evento=?')->execute([$idEvt]);
    db()->prepare('UPDATE huellas_digitales SET activa=0 WHERE id_catedratico=?')->execute([$idCat]);
    db()->prepare('INSERT INTO huellas_digitales (id_catedratico,template_data,sensor_slot,id_sensor,activa) VALUES (?,?,?,?,1)')->execute([$idCat,'registrado_via_sensor',$slot,$idS]);
    $c=db()->prepare('SELECT c.*,d.nombre AS departamento FROM catedraticos c JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE c.id_catedratico=?'); $c->execute([$idCat]);
    http_response_code(201); echo json_encode(['success'=>true,'message'=>'Huella registrada correctamente','catedratico'=>$c->fetch()]); exit;
}

// ── HUELLAS (modo legacy con MODO_REGISTRO en el ESP32) ───────
if ($uri==='/huellas/registrar' && $method==='POST') {
    sensorAuth(); $b=body(); $idS=(int)($b['id_sensor']??1); $slot=(int)($b['sensor_slot']??0); $idCat=(int)($b['id_catedratico']??0);
    if (!$slot||!$idCat) err('sensor_slot e id_catedratico requeridos');
    db()->prepare('UPDATE huellas_digitales SET activa=0 WHERE id_catedratico=?')->execute([$idCat]);
    db()->prepare('INSERT INTO huellas_digitales (id_catedratico,template_data,sensor_slot,id_sensor,activa) VALUES (?,?,?,?,1)')->execute([$idCat,'registrado_via_arduino',$slot,$idS]);
    http_response_code(201); echo json_encode(['message'=>'Huella registrada','success'=>true]); exit;
}

// ── REPORTES ──────────────────────────────────────────────────
if ($uri==='/reportes/generar' && $method==='POST') {
    auth(); $b=body(); $idDept=$b['id_departamento']??null; $periodo=$b['periodo']??date('Y-m');
    $deptNombre='Todos los departamentos';
    if ($idDept) { $d=db()->prepare('SELECT nombre FROM departamentos WHERE id_departamento=?'); $d->execute([$idDept]); $deptNombre=$d->fetchColumn()?:$deptNombre; }
    $lunes=date('Y-m-d',strtotime('monday this week')); $dias=[]; $noms=['Lun','Mar','Mie','Jue','Vie'];
    for ($i=0;$i<5;$i++) {
        $fecha=date('Y-m-d',strtotime("$lunes +$i days"));
        $s=db()->prepare("SELECT COUNT(*) FROM asistencias a JOIN catedraticos c ON c.id_catedratico=a.id_catedratico WHERE a.fecha=?".($idDept?" AND c.id_departamento=?":""));
        $s->execute($idDept?[$fecha,$idDept]:[$fecha]); $pres=(int)$s->fetchColumn();
        $t=db()->prepare("SELECT COUNT(*) FROM catedraticos WHERE activo=1".($idDept?" AND id_departamento=?":"")); $t->execute($idDept?[$idDept]:[]); $tot=(int)$t->fetchColumn();
        $dias[]=['dia'=>$noms[$i],'fecha'=>$fecha,'porcentaje'=>$tot>0?round($pres/$tot*100,1):0];
    }
    $mI=$periodo.'-01'; $mF=date('Y-m-t',strtotime($mI));
    $ts=db()->prepare("SELECT c.id_catedratico,c.nombre,c.apellido,d.nombre AS departamento,COUNT(*) AS ausencias FROM asistencias a JOIN catedraticos c ON c.id_catedratico=a.id_catedratico JOIN departamentos d ON d.id_departamento=c.id_departamento WHERE a.estado='ausente' AND a.fecha BETWEEN ? AND ?".($idDept?" AND c.id_departamento=?":"")." GROUP BY c.id_catedratico ORDER BY ausencias DESC LIMIT 5");
    $ts->execute($idDept?[$mI,$mF,$idDept]:[$mI,$mF]);
    $ps=db()->prepare("SELECT AVG(sub.pct) FROM (SELECT ROUND(SUM(CASE WHEN estado IN('presente','tardanza') THEN 1 ELSE 0 END)/COUNT(*)*100,0) AS pct FROM asistencias a JOIN catedraticos c ON c.id_catedratico=a.id_catedratico WHERE a.fecha BETWEEN ? AND ?".($idDept?" AND c.id_departamento=?":"")." GROUP BY a.fecha) sub");
    $ps->execute($idDept?[$mI,$mF,$idDept]:[$mI,$mF]);
    ok(['periodo'=>$periodo,'nombre_departamento'=>$deptNombre,'total_catedraticos'=>0,'promedio_asistencia'=>round((float)($ps->fetchColumn()?:0),0),'total_dias'=>5,'datos_semana'=>$dias,'top_ausencias'=>$ts->fetchAll()]);
}

err("Ruta no encontrada: $method $uri", 404);

} catch (PDOException $e) { err('BD: '.$e->getMessage(),500); }
  catch (Exception $e)    { err('Error: '.$e->getMessage(),500); }
