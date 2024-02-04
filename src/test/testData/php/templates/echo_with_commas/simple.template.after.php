<?php
$a = 5;
$newVarPhpExpression2 = 55;
echo <<<HEREDOC_DELIMITER
{$a}hello{$newVarPhpExpression2}
HEREDOC_DELIMITER;
