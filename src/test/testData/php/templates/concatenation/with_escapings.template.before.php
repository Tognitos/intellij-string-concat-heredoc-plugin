<?php
/**
 * this file has a string concatenations with:
 * - variables
 * - escaped single quotes
 * - escaped double quotes
 */
$existing = 'whatever';
$someVar = 'if<caret> ($(\'elementId\').innerHTML==\"\") {' .
    $existing . '; }'
    . '; $(\'elementId\').style.visibility=\'visible\'';
