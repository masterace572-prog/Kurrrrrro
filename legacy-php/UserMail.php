<?php
namespace App\Controllers;

use App\Models\CodeModel;
use App\Models\UserModel;
use CodeIgniter\Config\Services;

include('conn.php');
$url = "";
$CompName = "";

$this->userid = session()->userid;
$this->model = new UserModel();
$this->user = $this->model->getUser($this->userid);
$user = $this->user;
$username = getName($user);

$sql = "SELECT `email` from `users` where username='$username'";
$result = mysqli_query($conn, $sql);
$usermail = mysqli_fetch_array($result);

date_default_timezone_set('Asia/Calcutta');
$timestamp = date('d/m/Y h:i:sa');
$accesstime = date('h:i:sa');
$webpage = $_SERVER['REQUEST_URI'];
$browser = $_SERVER['HTTP_USER_AGENT'];
$url = $_SERVER['SERVER_NAME'];
$server = $_SERVER['HTTP_HOST'];

function getUserIP()
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
$user_ip = getUserIP();

    if (isset($username)||($email)) {  
        $email = \Config\Services::email();
        $email->setFrom('', '');
        $email->setTo($usermail);
        
        $email->setSubject("[$server]✔ Logged in as $username at $timestamp");
        $email->setMessage("<!DOCTYPE html>

");
$email->send();
}
?>