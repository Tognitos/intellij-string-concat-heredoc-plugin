<?php
$concatenationResult1 = 'Caret ' . 'is not' . ' here';
$someAssignment = <caret>5 + 15; // cursor is not on concat, hence no intention
$concatenationResult2 = 'Caret ' . "is also not here";
