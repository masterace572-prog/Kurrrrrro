<?php

$servername = "localhost";
$username = "modvipxy_WhatsApp";
$password = "modvipxy_WhatsApp";
$dbname = "modvipxy_WhatsApp";

$conn = mysqli_connect($servername,$username,$password,$dbname);

if(!$conn) {

die(" PROBLEM WITH CONNECTION : " . mysqli_connect_error());

}
  
?>