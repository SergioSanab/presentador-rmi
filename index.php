<?php
declare(strict_types=1);

$dbFile = __DIR__ . '/lab.db';

if (!class_exists('SQLite3')) {
    die('SQLite3 no está instalado. Instale php-sqlite3 y reinicie Apache.');
}

$db = new SQLite3($dbFile);

$db->exec(
    'CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT NOT NULL,
        email TEXT NOT NULL,
        role TEXT NOT NULL
    )'
);

/*
 * Crear registros de prueba si la tabla está vacía.
 */
$countResult = $db->querySingle('SELECT COUNT(*) FROM users');

if ((int)$countResult === 0) {
    $db->exec(
        "INSERT INTO users (username, email, role) VALUES
        ('ana', 'ana@lab.local', 'user'),
        ('carlos', 'carlos@lab.local', 'user'),
        ('admin', 'admin@lab.local', 'administrator')"
    );
}

$results = [];
$error = '';

if (isset($_GET['q'])) {

    $q = $_GET['q'];

    /*
     * ==========================================================
     * VULNERABILIDAD SQL INJECTION
     * ==========================================================
     *
     * El parámetro "q" proviene directamente de la petición HTTP.
     * Posteriormente se concatena directamente dentro de la
     * consulta SQL.
     *
     * NO HACER ESTO EN UNA APLICACIÓN REAL.
     */

    $sql = "SELECT id, username, email, role
            FROM users
            WHERE username LIKE '%$q%'
               OR email LIKE '%$q%'
               OR role LIKE '%$q%'";

    $queryResult = $db->query($sql);

    if ($queryResult === false) {
        $error = 'Error SQL: ' . $db->lastErrorMsg();
    } else {

        while ($row = $queryResult->fetchArray(SQLITE3_ASSOC)) {
            $results[] = $row;
        }
    }
}
?>

<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>SQL Injection Lab</title>

    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 900px;
            margin: 50px auto;
            padding: 20px;
            background: #f5f5f5;
        }

        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
        }

        input {
            width: 100%;
            padding: 12px;
            margin-top: 8px;
            margin-bottom: 15px;
            box-sizing: border-box;
        }

        button {
            padding: 12px 20px;
            cursor: pointer;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 25px;
        }

        th, td {
            border: 1px solid #ccc;
            padding: 10px;
            text-align: left;
        }

        th {
            background: #eee;
        }

        .error {
            background: #ffe0e0;
            padding: 15px;
            margin-top: 20px;
        }

        .info {
            background: #e5f1ff;
            padding: 15px;
            margin-bottom: 20px;
        }
    </style>
</head>

<body>

<div class="container">

    <h1>SQL Injection Laboratory</h1>

    <div class="info">
        Aplicación web creada exclusivamente para prácticas
        controladas de seguridad informática.
    </div>

    <h2>Buscar usuarios</h2>

    <form method="GET">

        <label for="q">
            Nombre de usuario:
        </label>

        <input
            type="text"
            id="q"
            name="q"
            placeholder="Ejemplo: ana"
        >

        <button type="submit">
            Buscar
        </button>

    </form>

    <?php if ($error !== ''): ?>

        <div class="error">
            <strong>Error:</strong>
            <?= htmlspecialchars(
                $error,
                ENT_QUOTES,
                'UTF-8'
            ) ?>
        </div>

    <?php endif; ?>

    <?php if (isset($_GET['q']) && $error === ''): ?>

        <h2>Resultados</h2>

        <?php if (count($results) === 0): ?>

            <p>
                No se encontraron resultados.
            </p>

        <?php else: ?>

            <table>

                <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Email</th>
                    <th>Rol</th>
                </tr>

                <?php foreach ($results as $row): ?>

                    <tr>

                        <td>
                            <?= (int)$row['id'] ?>
                        </td>

                        <td>
                            <?= htmlspecialchars(
                                $row['username'],
                                ENT_QUOTES,
                                'UTF-8'
                            ) ?>
                        </td>

                        <td>
                            <?= htmlspecialchars(
                                $row['email'],
                                ENT_QUOTES,
                                'UTF-8'
                            ) ?>
                        </td>

                        <td>
                            <?= htmlspecialchars(
                                $row['role'],
                                ENT_QUOTES,
                                'UTF-8'
                            ) ?>
                        </td>

                    </tr>

                <?php endforeach; ?>

            </table>

        <?php endif; ?>

    <?php endif; ?>

</div>

</body>
</html>
