<?php
/**
 * this file has a string concatenations with:
 * - variables
 * - escaped single quotes
 * - escaped double quotes
 */
$existing = 'whatever';
$someVar = <<<HEREDOC_DELIMITER
if (\$('elementId').innerHTML==\\"\\") {{$existing}; }; \$('elementId').style.visibility='visible'
HEREDOC_DELIMITER;
