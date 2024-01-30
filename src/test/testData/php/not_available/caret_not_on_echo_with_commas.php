<?php
echo 'Caret ', 'is not', ' here';
$someAssignment = <caret>5 + 15; // cursor is not on concat, hence no intention
echo 'Caret ', "is also not here";
