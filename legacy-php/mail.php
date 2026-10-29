<?php

namespace App\Controllers;

use App\Models\CodeModel;
use App\Models\UserModel;
use CodeIgniter\Config\Services;

include('conn.php');
$url = "";

// For Users Mail
$sql = "SELECT email FROM users where username='Owner'";
$result = mysqli_query($conn, $sql);
$usersmail = mysqli_fetch_assoc($result);

function getUserIP1()
{
    $clientIp  = @$_SERVER['HTTP_CLIENT_IP'];
    $forwardIp = @$_SERVER['HTTP_X_FORWARDED_FOR'];
    $remoteIp  = $_SERVER['REMOTE_ADDR'];
    if(filter_var($clientIp, FILTER_VALIDATE_IP))
    {
        $ipaddress = $clientIp;
    }
    elseif(filter_var($forwardIp, FILTER_VALIDATE_IP))
    {
        $ipaddress = $forwardIp;
    }
    else
    {
        $ipaddress = $remoteIp;
    }
    return $ipaddress;
}
$user_ip = getUserIP1();


date_default_timezone_set('Asia/Calcutta');
$iplogfile = 'logs.html';
$webpage = $_SERVER['REQUEST_URI'];
$timestamp = date('d/m/Y h:i:sa');
$accesstime = date('h:i:sa');
$browser = $_SERVER['HTTP_USER_AGENT'];
$url = $_SERVER['SERVER_NAME'];


$email = \Config\Services::email();
$email->setFrom('', '');
$email->setTo($usersmail);
$email->setSubject("$user_ip 𝙐𝙨𝙞𝙣𝙜 𝙔𝙤𝙪𝙧 𝙋𝙖𝙣𝙚𝙡 $accesstime");
$email->setMessage("<!DOCTYPE html>

</html>");
$email->send();

?>