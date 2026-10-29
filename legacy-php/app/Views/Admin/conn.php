<?php

$servername = "localhost";
$username = "modvipxy";
$password = "modvipxy";
$dbname = "modvipxy";

$conn = mysqli_connect($servername,$username,$password,$dbname);

if(!$conn) {

die(" PROBLEM WITH CONNECTION : " . mysqli_connect_error());

}
  
?>